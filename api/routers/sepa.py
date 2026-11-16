"""Consultas en vivo a SEPA / Precios Claros (sucursales, precios, comparacion)."""
import math
from datetime import datetime, timezone
from typing import Optional

import httpx
from fastapi import APIRouter, Query
from pydantic import BaseModel

from api.sources import SEPA_BASE_URL, SEPA_HEADERS

router = APIRouter()


class SupermarketResponse(BaseModel):
    id: str
    chain_id: str
    name: str
    latitude: float
    longitude: float
    distance_km: float


class PriceResponse(BaseModel):
    product_id: str
    supermarket_id: str
    price: str
    last_updated: str


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(dlon / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


@router.get("/supermarkets", response_model=list[SupermarketResponse])
async def get_supermarkets(
    lat: float = Query(...), lng: float = Query(...),
    radius_km: float = Query(30.0),
):
    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        try:
            r = await client.get(f"{SEPA_BASE_URL}/sucursales", params={"lat": lat, "lng": lng})
            r.raise_for_status()
            data = r.json()
        except Exception:
            return []

    results = []
    for s in data.get("sucursales", data if isinstance(data, list) else []):
        s_lat, s_lng = float(s.get("lat", 0)), float(s.get("lng", 0))
        dist = haversine_km(lat, lng, s_lat, s_lng)
        if dist <= radius_km:
            results.append(SupermarketResponse(
                id=str(s.get("id", "")),
                chain_id=str(s.get("comercioId", s.get("banderaId", ""))),
                name=s.get("comercioRazonSocial", s.get("banderaDescripcion", "Desconocido")),
                latitude=s_lat, longitude=s_lng, distance_km=round(dist, 2),
            ))
    results.sort(key=lambda x: x.distance_km)
    return results


@router.get("/prices", response_model=list[PriceResponse])
async def get_prices(
    product_ids: str = Query(...), supermarket_ids: str = Query(...),
):
    products = [p.strip() for p in product_ids.split(",") if p.strip()]
    supermarkets = [s.strip() for s in supermarket_ids.split(",") if s.strip()]
    results = []
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")

    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        for pid in products:
            try:
                r = await client.get(f"{SEPA_BASE_URL}/producto",
                    params={"id_producto": pid, "array_sucursales": ",".join(supermarkets)})
                r.raise_for_status()
                data = r.json()
                for p in data.get("precios", data if isinstance(data, list) else []):
                    results.append(PriceResponse(
                        product_id=pid,
                        supermarket_id=str(p.get("sucursal_id", "")),
                        price=str(p.get("precio", "0")),
                        last_updated=p.get("fecha", now),
                    ))
            except Exception:
                continue
    return results


@router.get("/search")
async def search_products(
    q: str = Query(...),
    lat: Optional[float] = Query(None), lng: Optional[float] = Query(None),
):
    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        try:
            r = await client.get(f"{SEPA_BASE_URL}/productos",
                params={"string": q, "lat": lat or -34.6, "lng": lng or -58.4})
            r.raise_for_status()
            return r.json()
        except Exception:
            return []


@router.get("/compare")
async def compare_prices(
    products: str = Query(..., description="Nombres de productos separados por coma"),
    lat: float = Query(-34.6037),
    lng: float = Query(-58.3816),
    radius_km: float = Query(30.0),
):
    """
    Busca precios de productos en supermercados cercanos.
    Recibe nombres de productos, busca en SEPA, y devuelve precios comparados.
    """
    product_names = [p.strip() for p in products.split(",") if p.strip()]
    
    # 1. Get nearby supermarkets
    supermarkets = []
    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        try:
            r = await client.get(f"{SEPA_BASE_URL}/sucursales", params={"lat": lat, "lng": lng})
            r.raise_for_status()
            data = r.json()
            for s in data.get("sucursales", []):
                s_lat, s_lng = float(s.get("lat", 0)), float(s.get("lng", 0))
                dist = haversine_km(lat, lng, s_lat, s_lng)
                if dist <= radius_km:
                    supermarkets.append({
                        "id": str(s.get("id", "")),
                        "name": s.get("comercioRazonSocial", s.get("banderaDescripcion", "Desconocido")),
                        "distance_km": round(dist, 2)
                    })
            supermarkets.sort(key=lambda x: x["distance_km"])
            supermarkets = supermarkets[:10]  # Top 10 closest
        except Exception:
            pass

    if not supermarkets:
        return {"products": [], "supermarkets": [], "message": "No se encontraron supermercados cercanos"}

    # 2. Search each product in SEPA
    results = []
    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        for product_name in product_names:
            try:
                r = await client.get(f"{SEPA_BASE_URL}/productos",
                    params={"string": product_name, "lat": lat, "lng": lng})
                r.raise_for_status()
                data = r.json()
                
                sepa_products = data.get("productos", data if isinstance(data, list) else [])
                if not sepa_products:
                    results.append({
                        "name": product_name,
                        "sepa_name": None,
                        "prices": [],
                        "message": "Producto no encontrado en SEPA"
                    })
                    continue

                # Use first match
                sepa_product = sepa_products[0]
                sepa_id = sepa_product.get("id", "")
                sepa_name = sepa_product.get("nombre", product_name)

                # Get prices for this product at nearby supermarkets
                supermarket_ids = ",".join([s["id"] for s in supermarkets])
                r2 = await client.get(f"{SEPA_BASE_URL}/producto",
                    params={"id_producto": sepa_id, "array_sucursales": supermarket_ids})
                r2.raise_for_status()
                price_data = r2.json()

                prices = []
                for p in price_data.get("precios", price_data if isinstance(price_data, list) else []):
                    suc_id = str(p.get("sucursal_id", ""))
                    suc_info = next((s for s in supermarkets if s["id"] == suc_id), None)
                    if suc_info:
                        prices.append({
                            "supermarket": suc_info["name"],
                            "supermarket_id": suc_id,
                            "distance_km": suc_info["distance_km"],
                            "price": p.get("precio", 0),
                            "date": p.get("fecha", "")
                        })

                prices.sort(key=lambda x: x.get("price", 0))
                results.append({
                    "name": product_name,
                    "sepa_name": sepa_name,
                    "prices": prices[:5],  # Top 5 cheapest
                })
            except Exception:
                results.append({
                    "name": product_name,
                    "sepa_name": None,
                    "prices": [],
                    "message": "Error al buscar precios"
                })

    return {
        "products": results,
        "supermarkets": supermarkets,
        "total_supermarkets": len(supermarkets)
    }
