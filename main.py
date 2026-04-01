"""
Backend de precios para Listas de Compras Compartidas.
Consulta SEPA/Precios Claros y expone una API limpia para la app Android.
"""

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
import httpx
import math
from datetime import datetime

app = FastAPI(title="Precios API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

SEPA_BASE_URL = "https://d3e6htiiul5ek9.cloudfront.net/prod"
SEPA_HEADERS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}


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


@app.get("/supermarkets", response_model=list[SupermarketResponse])
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


@app.get("/prices", response_model=list[PriceResponse])
async def get_prices(
    product_ids: str = Query(...), supermarket_ids: str = Query(...),
):
    products = [p.strip() for p in product_ids.split(",") if p.strip()]
    supermarkets = [s.strip() for s in supermarket_ids.split(",") if s.strip()]
    results = []
    now = datetime.utcnow().isoformat() + "Z"

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


@app.get("/search")
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


@app.get("/health")
async def health():
    return {"status": "ok"}


# ─── Voice Command Processing (replaces Firebase Cloud Functions) ─────────────

import firebase_admin
from firebase_admin import credentials, firestore
import os, re, uuid

# Initialize Firebase Admin SDK (optional - voice endpoints need it, price endpoints don't)
_firebase_initialized = False
db = None
try:
    if not firebase_admin._apps:
        if os.path.exists("serviceAccountKey.json"):
            cred = credentials.Certificate("serviceAccountKey.json")
            firebase_admin.initialize_app(cred)
        elif os.environ.get("FIREBASE_SERVICE_ACCOUNT"):
            import json
            sa_info = json.loads(os.environ["FIREBASE_SERVICE_ACCOUNT"])
            cred = credentials.Certificate(sa_info)
            firebase_admin.initialize_app(cred)
        else:
            firebase_admin.initialize_app()
    db = firestore.client()
    _firebase_initialized = True
except Exception as e:
    print(f"Firebase init skipped: {e}. Voice endpoints will not work.")

PRODUCT_CATALOG = [
    "leche entera", "leche descremada", "pan blanco", "pan integral",
    "arroz blanco", "arroz integral", "queso cremoso", "queso rallado",
    "yogur natural", "yogur frutilla", "manzana", "banana", "tomate",
    "aceite de girasol", "aceite de oliva", "azúcar", "sal fina",
    "harina", "fideos", "manteca", "huevos", "pollo", "carne picada",
    "agua mineral", "gaseosa", "cerveza", "vino",
]


def find_matching_products(name: str) -> list[str]:
    n = name.lower().strip()
    exact = [p for p in PRODUCT_CATALOG if p == n]
    if exact:
        return exact
    prefix = [p for p in PRODUCT_CATALOG if p.startswith(n)]
    if prefix:
        return prefix
    return [p for p in PRODUCT_CATALOG if n in p]


def parse_multiple_products(raw: str) -> list[str]:
    return [s.strip() for s in re.split(r",|\by\b|\band\b", raw, flags=re.IGNORECASE) if s.strip()]


class VoiceCommand(BaseModel):
    userId: str
    action: str = "ADD_PRODUCT"
    productName: str
    listName: Optional[str] = None
    quantity: Optional[int] = None
    unit: Optional[str] = None


@app.post("/voice/google")
async def process_google_assistant(body: dict):
    if not _firebase_initialized:
        return {"fulfillmentText": "Servicio de voz no disponible."}
    params = body.get("queryResult", {}).get("parameters", {})
    user_id = (body.get("originalDetectIntentRequest", {}).get("payload", {}).get("user", {}).get("userId")
               or body.get("session", ""))
    product_name = params.get("product", "")
    if not product_name:
        return {"fulfillmentText": "No se pudo interpretar el comando de voz."}

    cmd = VoiceCommand(
        userId=str(user_id), productName=product_name,
        listName=params.get("list"), quantity=params.get("quantity"),
        unit=params.get("unit")
    )
    result = await process_voice_command(cmd)
    if result["type"] == "disambiguation":
        return {"fulfillmentText": f"¿Cuál producto querés agregar? {', '.join(result['suggestions'])}"}
    return {"fulfillmentText": result["message"]}


@app.post("/voice/alexa")
async def process_alexa_skill(body: dict):
    if not _firebase_initialized:
        return alexa_response("Servicio de voz no disponible.", True)
    req = body.get("request", {})
    
    # LaunchRequest: user said "abrí super"
    if req.get("type") == "LaunchRequest":
        return alexa_response("¿Qué producto querés agregar?", False)
    
    if req.get("type") != "IntentRequest":
        return alexa_response("¿Qué producto querés agregar?", False)

    slots = req.get("intent", {}).get("slots", {})
    user_id = get_uid_from_alexa_request(body)
    product_name = slots.get("product", {}).get("value", "")
    if not product_name:
        return alexa_response("No entendí. ¿Qué producto querés agregar?", False)

    cmd = VoiceCommand(
        userId=user_id, productName=product_name,
        listName=slots.get("list", {}).get("value"),
        quantity=slots.get("quantity", {}).get("value"),
        unit=slots.get("unit", {}).get("value")
    )
    result = await process_voice_command(cmd)
    # After adding, ask if they want to add more
    return alexa_response(result["message"] + " ¿Querés agregar algo más?", False)


def alexa_response(text: str, end_session: bool) -> dict:
    return {"version": "1.0", "response": {"outputSpeech": {"type": "PlainText", "text": text}, "shouldEndSession": end_session}}


async def process_voice_command(cmd: VoiceCommand) -> dict:
    if not cmd.productName.strip():
        return {"type": "error", "message": "No se especificó un producto."}

    list_name = (cmd.listName or "").strip()
    product_names = parse_multiple_products(cmd.productName)

    # Find existing list: if no name specified, use the first list of the user
    lists_ref = db.collection("shoppingLists")
    if list_name:
        query = lists_ref.where("ownerId", "==", cmd.userId).where("name", "==", list_name).limit(1).get()
    else:
        query = lists_ref.where("ownerId", "==", cmd.userId).limit(1).get()

    if not query:
        actual_name = list_name or "Mi Lista"
        new_ref = lists_ref.document()
        new_ref.set({"name": actual_name, "ownerId": cmd.userId, "members": [cmd.userId],
                     "isShared": False, "createdAt": firestore.SERVER_TIMESTAMP, "updatedAt": firestore.SERVER_TIMESTAMP})
        list_id = new_ref.id
    else:
        list_id = query[0].id
        actual_name = query[0].to_dict().get("name", "Mi Lista")

    # Search for existing product data: first in knownProducts collection, then in user's lists
    def find_existing_product(name):
        try:
            # Try exact match first, then capitalized
            for search_name in [name, name.capitalize(), name.title(), name.lower()]:
                known = db.collection("knownProducts").where("ownerId", "==", cmd.userId).where("name", "==", search_name).limit(1).get()
                if known:
                    return known[0].to_dict()
            # Fallback: search in user's existing lists
            user_lists = lists_ref.where("ownerId", "==", cmd.userId).get()
            for lst in user_lists:
                for search_name in [name, name.capitalize(), name.title()]:
                    products = db.collection("shoppingLists").document(lst.id).collection("products").where("name", "==", search_name).limit(1).get()
                    if products:
                        return products[0].to_dict()
        except Exception:
            pass
        return {}

    added = []
    for raw_name in product_names:
        name = raw_name.strip().capitalize()
        existing = find_existing_product(name)
        product_data = {
            "name": name, "quantity": cmd.quantity or 1,
            "unit": existing.get("unit", cmd.unit or "Unidad"),
            "categoryId": existing.get("categoryId", ""),
            "categoryName": existing.get("categoryName", "Otros"),
            "categoryEmoji": existing.get("categoryEmoji", "📦"),
            "emoji": existing.get("emoji", ""),
            "isPurchased": False, "lastModifiedBy": cmd.userId,
            "lastModifiedAt": firestore.SERVER_TIMESTAMP
        }
        db.collection("shoppingLists").document(list_id).collection("products").document().set(product_data)
        added.append(name)

    db.collection("shoppingLists").document(list_id).update({"updatedAt": firestore.SERVER_TIMESTAMP})

    if len(added) == 1:
        return {"type": "success", "message": f"Listo, agregué {added[0]} a la lista {actual_name}."}
    return {"type": "success", "message": f"Listo, agregué {len(added)} productos a la lista {actual_name}."}



# ─── Alexa Account Linking (OAuth-like flow) ──────────────────────────────────
from fastapi import Request, Form
from fastapi.responses import HTMLResponse, RedirectResponse
import urllib.parse

# Simple token store: maps access_token -> firebase_uid
_token_store: dict[str, str] = {}

@app.get("/auth/login", response_class=HTMLResponse)
async def auth_login_page(
    redirect_uri: str = "",
    state: str = "",
    client_id: str = "",
    response_type: str = ""
):
    """Login page shown to user during Alexa Account Linking."""
    return f"""
    <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <style>body{{font-family:sans-serif;max-width:400px;margin:40px auto;padding:20px}}
    input{{width:100%;padding:12px;margin:8px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px}}
    button{{width:100%;padding:14px;background:#4CAF50;color:white;border:none;border-radius:4px;font-size:16px;cursor:pointer}}
    h2{{text-align:center}}</style></head>
    <body><h2>🛒 Uh no había</h2><p>Iniciá sesión para vincular tu cuenta con Alexa</p>
    <form method="post" action="/auth/token">
    <input type="hidden" name="redirect_uri" value="{redirect_uri}">
    <input type="hidden" name="state" value="{state}">
    <input type="email" name="email" placeholder="Correo electrónico" required>
    <input type="password" name="password" placeholder="Contraseña" required>
    <button type="submit">Vincular cuenta</button>
    </form></body></html>
    """

@app.post("/auth/token")
async def auth_token(
    redirect_uri: str = Form(""),
    state: str = Form(""),
    email: str = Form(""),
    password: str = Form("")
):
    """Authenticate user and redirect back to Alexa with access token."""
    try:
        # Verify credentials with Firebase Auth REST API
        api_key = os.environ.get("FIREBASE_API_KEY", "")
        async with httpx.AsyncClient() as client:
            r = await client.post(
                f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}",
                json={"email": email, "password": password, "returnSecureToken": True}
            )
            if r.status_code != 200:
                return HTMLResponse("<h2>Error: credenciales inválidas</h2><a href='javascript:history.back()'>Volver</a>")
            data = r.json()
            uid = data["localId"]
            token = uuid.uuid4().hex  # Simple 32-char hex token

        _token_store[token] = uid

        # Must use client-side redirect because HTTP 302 doesn't preserve URL fragments
        fragment = f"access_token={token}&token_type=Bearer&state={urllib.parse.quote(state, safe='')}"
        redirect_url = redirect_uri + "#" + fragment
        return HTMLResponse(
            f'<html><body><script>window.location.replace("{redirect_url}");</script></body></html>',
            status_code=200
        )
    except Exception as e:
        return HTMLResponse(f"<h2>Error: {e}</h2><a href='javascript:history.back()'>Volver</a>")


def get_uid_from_alexa_request(body: dict) -> str:
    """Extract Firebase UID from Alexa request using the access token."""
    token = body.get("session", {}).get("user", {}).get("accessToken", "")
    if token and token in _token_store:
        return _token_store[token]
    # Default user - hardcoded for now until Account Linking works
    return "P46tbJbTrzQHGocCqI4CgRJwHah2"


# ─── Smart Price Comparison (search by product name) ──────────────────────────

@app.get("/compare")
async def compare_prices(
    products: str = Query(..., description="Nombres de productos separados por coma"),
    lat: float = Query(...), lng: float = Query(...),
    radius_km: float = Query(30.0),
):
    """
    Busca productos por nombre en SEPA, encuentra supermercados cercanos,
    y devuelve precios comparativos.
    """
    product_names = [p.strip() for p in products.split(",") if p.strip()]
    
    # 1. Get nearby supermarkets
    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        try:
            r = await client.get(f"{SEPA_BASE_URL}/sucursales", params={"lat": lat, "lng": lng})
            r.raise_for_status()
            data = r.json()
        except Exception:
            return {"supermarkets": [], "products": [], "error": "No se pudieron obtener supermercados"}

    supermarkets = []
    for s in data.get("sucursales", []):
        s_lat, s_lng = float(s.get("lat", 0)), float(s.get("lng", 0))
        dist = haversine_km(lat, lng, s_lat, s_lng)
        if dist <= radius_km:
            supermarkets.append({
                "id": str(s.get("id", "")),
                "name": s.get("comercioRazonSocial", s.get("banderaDescripcion", "?")),
                "distance_km": round(dist, 2)
            })
    supermarkets.sort(key=lambda x: x["distance_km"])
    supermarkets = supermarkets[:10]  # Top 10 closest

    if not supermarkets:
        return {"supermarkets": [], "products": [], "error": "No hay supermercados cercanos"}

    sucursal_ids = [s["id"] for s in supermarkets]

    # 2. Search each product in SEPA and get prices
    results = []
    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        for product_name in product_names:
            try:
                # Search product
                r = await client.get(f"{SEPA_BASE_URL}/productos",
                    params={"string": product_name, "lat": lat, "lng": lng})
                r.raise_for_status()
                search_data = r.json()
                
                productos = search_data.get("productos", search_data if isinstance(search_data, list) else [])
                if not productos:
                    results.append({"name": product_name, "prices": [], "sepa_name": None})
                    continue

                # Take first match
                sepa_product = productos[0]
                sepa_id = str(sepa_product.get("id", ""))
                sepa_name = sepa_product.get("nombre", product_name)

                # Get prices at nearby supermarkets
                r2 = await client.get(f"{SEPA_BASE_URL}/producto",
                    params={"id_producto": sepa_id, "array_sucursales": ",".join(sucursal_ids)})
                r2.raise_for_status()
                price_data = r2.json()

                precios = price_data.get("precios", price_data if isinstance(price_data, list) else [])
                
                product_prices = []
                for p in precios:
                    product_prices.append({
                        "supermarket_id": str(p.get("sucursal_id", "")),
                        "price": str(p.get("precio", "0")),
                        "date": p.get("fecha", "")
                    })

                results.append({
                    "name": product_name,
                    "sepa_name": sepa_name,
                    "prices": product_prices
                })
            except Exception:
                results.append({"name": product_name, "prices": [], "sepa_name": None})

    return {
        "supermarkets": supermarkets,
        "products": results
    }


# ─── Compare Prices Endpoint ─────────────────────────────────────────────────

@app.get("/compare")
async def compare_prices(
    products: str = Query(..., description="Nombres de productos separados por coma"),
    lat: float = Query(...), lng: float = Query(...),
    radius_km: float = Query(30.0),
):
    product_names = [p.strip() for p in products.split(",") if p.strip()]
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
                    supermarkets.append({"id": str(s.get("id", "")), "name": s.get("comercioRazonSocial", s.get("banderaDescripcion", "?")), "distance_km": round(dist, 2)})
            supermarkets.sort(key=lambda x: x["distance_km"])
            supermarkets = supermarkets[:10]
        except Exception:
            pass
    if not supermarkets:
        return {"products": [], "supermarkets": [], "error": "No se encontraron supermercados cercanos"}
    results = []
    async with httpx.AsyncClient(timeout=15.0, headers=SEPA_HEADERS) as client:
        for pname in product_names:
            try:
                r = await client.get(f"{SEPA_BASE_URL}/productos", params={"string": pname, "lat": lat, "lng": lng})
                r.raise_for_status()
                sd = r.json()
                sepa_prods = sd if isinstance(sd, list) else sd.get("productos", [])
                if not sepa_prods:
                    results.append({"name": pname, "prices": [], "sepa_match": None})
                    continue
                sp = sepa_prods[0]
                sid = sp.get("id", "")
                sname = sp.get("nombre", pname)
                sids = ",".join([s["id"] for s in supermarkets])
                r2 = await client.get(f"{SEPA_BASE_URL}/producto", params={"id_producto": sid, "array_sucursales": sids})
                r2.raise_for_status()
                pd2 = r2.json()
                precios = pd2 if isinstance(pd2, list) else pd2.get("precios", [])
                prices = []
                for p in precios:
                    pv = p.get("precio", p.get("precioLista", 0))
                    suc_id = str(p.get("sucursal_id", p.get("sucursalId", "")))
                    si = next((s for s in supermarkets if s["id"] == suc_id), None)
                    if si and pv:
                        prices.append({"supermarket": si["name"], "distance_km": si["distance_km"], "price": float(pv)})
                prices.sort(key=lambda x: x["price"])
                results.append({"name": pname, "sepa_match": sname, "prices": prices})
            except Exception:
                results.append({"name": pname, "prices": [], "sepa_match": None})
    return {"products": results, "supermarkets": supermarkets}


# ─── Price Comparison Endpoint ────────────────────────────────────────────────

@app.get("/compare")
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


# ─── Supermarket Catalog (VTEX Scraper + Postgres/Neon) ───────────────────────
import psycopg2
from psycopg2.extras import execute_values
import threading, time as _time, requests as _requests

_DB_URL = os.environ.get("DATABASE_URL", "")

def _pg():
    return psycopg2.connect(_DB_URL) if _DB_URL else None

# Supermarkets with VTEX APIs
_VTEX_STORES = {
    "DIA": "https://diaonline.supermercadosdia.com.ar",
    "Jumbo": "https://www.jumbo.com.ar",
    "Disco": "https://www.disco.com.ar",
    "Carrefour": "https://www.carrefour.com.ar",
}

def _seed_promos():
    """Seed common bank promotions for supermarkets."""
    if not _DB_URL: return
    conn = _pg(); cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM promos_bancarias"); count = cur.fetchone()[0]
    if count > 0:
        cur.close(); conn.close(); return
    promos = [
        ("Carrefour","Banco Carrefour","Tarjeta Carrefour",20,None,None,None,None,"20% con Tarjeta de Crédito Carrefour"),
        ("Carrefour","Cuenta Digital Carrefour","Cuenta Digital",10,None,None,None,None,"10% con Cuenta Digital Carrefour"),
        ("Carrefour","BBVA","Visa/Mastercard",15,"Miércoles",15000,None,None,"15% los miércoles con BBVA, tope $15.000"),
        ("DIA","Cuenta DNI","Cuenta DNI",10,None,10000,None,None,"10% con Cuenta DNI Provincia, tope $10.000"),
        ("DIA","Club DIA","Club DIA",10,None,None,None,None,"10% con Club DIA en productos seleccionados"),
        ("DIA","Banco Nación","Visa/Mastercard",20,"Martes",15000,None,None,"20% los martes con Banco Nación, tope $15.000"),
        ("Jumbo","Banco Galicia","Visa/Mastercard",15,"Sábados",12000,None,None,"15% los sábados con Galicia, tope $12.000"),
        ("Jumbo","Cencosud","Tarjeta Cencosud",15,None,None,None,None,"15% con Tarjeta Cencosud"),
        ("Disco","Cencosud","Tarjeta Cencosud",15,None,None,None,None,"15% con Tarjeta Cencosud"),
        ("Disco","Banco Galicia","Visa/Mastercard",15,"Sábados",12000,None,None,"15% los sábados con Galicia, tope $12.000"),
        ("Changomas","Banco Provincia","Cuenta DNI",15,"Miércoles",10000,None,None,"15% los miércoles con Cuenta DNI, tope $10.000"),
        ("Coto","ICBC","Visa/Mastercard",30,"Lunes",15000,None,None,"30% los lunes con ICBC, tope $15.000"),
        ("Coto","Banco Nación","Visa/Mastercard",20,"Jueves",12000,None,None,"20% los jueves con Banco Nación, tope $12.000"),
        ("Coto","Comunidad Coto","Comunidad Coto",5,None,None,None,None,"5% con Comunidad Coto en productos seleccionados"),
    ]
    for p in promos:
        cur.execute("INSERT INTO promos_bancarias (cadena,banco,tarjeta,descuento_pct,dia_semana,tope_reintegro,vigencia_desde,vigencia_hasta,condiciones) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)", p)
    conn.commit(); cur.close(); conn.close()

def _seed_medios_pago():
    """Seed master list of payment methods."""
    if not _DB_URL: return
    conn = _pg(); cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM medios_pago"); count = cur.fetchone()[0]
    if count > 0:
        cur.close(); conn.close(); return
    bancos = [
        ("BBVA","Visa,Mastercard,American Express"),("Bica","Visa,Mastercard"),("Ciudad","Visa,Mastercard"),
        ("Coinag","Visa,Mastercard"),("Columbia","Visa,Mastercard"),("Comafi","Visa,Mastercard"),
        ("Credicoop","Visa,Mastercard,Cabal"),("Corrientes","Visa,Mastercard"),("Tierra del Fuego","Visa,Mastercard"),
        ("Chubut","Visa,Mastercard"),("Del Sol","Visa,Mastercard"),("Entre Ríos","Visa,Mastercard"),
        ("Formosa","Visa,Mastercard"),("Galicia","Visa,Mastercard,American Express"),
        ("Hipotecario","Visa,Mastercard"),("HSBC","Visa,Mastercard"),("ICBC","Visa,Mastercard"),
        ("Industrial (BIND)","Visa,Mastercard"),("La Pampa","Visa,Mastercard"),
        ("Macro","Visa,Mastercard"),("Nación","Visa,Mastercard"),("Patagonia","Visa,Mastercard"),
        ("Provincia","Visa,Mastercard"),("Roela","Visa,Mastercard"),("San Juan","Visa,Mastercard"),
        ("Santa Cruz","Visa,Mastercard"),("Santa Fe","Visa,Mastercard"),("Santander","Visa,Mastercard,American Express"),
        ("Supervielle","Visa,Mastercard"),
    ]
    billeteras = [
        "Apple Pay","Astropay","Axion ON","Billetera Santa Fe","BNA+","Cenco Pay",
        "Claro Pay","Cuenta DNI","GoCuotas","Lemon","Mercado Pago","MODO",
        "n1u","Nubi","Personal Pay","Prex","Shell Box","Ualá","Billetera Córdoba",
        "Wayni","Wibond","Naranja X","Tap","YOY",
    ]
    clubes = [
        "¡Appa!","Beneficios La Capital","Club Crónica","Club La Nación",
        "Automóvil Club Argentino (ACA)","Clarín 365","Club DIA","Comunidad Coto","Tarjeta Cencosud",
    ]
    for nombre, tarjetas in bancos:
        cur.execute("INSERT INTO medios_pago (nombre,tipo,nombre_display,tarjetas_disponibles) VALUES (%s,'banco',%s,%s)",
            (nombre, f"Banco {nombre}", tarjetas))
    for nombre in billeteras:
        cur.execute("INSERT INTO medios_pago (nombre,tipo,nombre_display) VALUES (%s,'billetera_digital',%s)",
            (nombre, nombre))
    for nombre in clubes:
        cur.execute("INSERT INTO medios_pago (nombre,tipo,nombre_display) VALUES (%s,'club_beneficios',%s)",
            (nombre, nombre))
    conn.commit()
    # Link existing promos to medios_pago
    cur.execute("SELECT id,nombre FROM medios_pago")
    mp_map = {r[1].lower(): r[0] for r in cur.fetchall()}
    cur.execute("SELECT id,banco FROM promos_bancarias WHERE medio_pago_id IS NULL")
    for pid, banco in cur.fetchall():
        mp_id = mp_map.get(banco.lower()) or mp_map.get(f"banco {banco}".lower())
        if mp_id:
            cur.execute("UPDATE promos_bancarias SET medio_pago_id=%s WHERE id=%s", (mp_id, pid))
    conn.commit(); cur.close(); conn.close()

# More specific search terms for better results
_SCRAPE_TERMS = [
    "leche entera", "leche descremada", "leche larga vida",
    "arroz largo fino", "arroz integral", "fideos spaghetti", "fideos tirabuzón",
    "aceite girasol", "aceite oliva", "harina 000", "azúcar",
    "yerba mate", "café instantáneo", "té en saquitos",
    "galletitas dulces", "galletitas saladas", "pan lactal", "pan integral",
    "manteca", "queso cremoso", "queso rallado", "yogur natural", "yogur frutilla",
    "huevos", "pollo entero", "pechuga pollo", "carne picada", "nalga",
    "jamón cocido", "salchichas", "atún en lata",
    "tomate perita", "puré de tomate", "mayonesa", "mostaza", "ketchup", "sal fina",
    "cerveza lata", "vino tinto", "gaseosa cola", "agua mineral", "jugo naranja",
    "detergente líquido", "jabón en polvo", "shampoo", "papel higiénico",
    "lavandina", "desodorante", "pasta dental", "pañales",
    "mermelada", "dulce de leche", "cereales", "avena", "polenta", "lentejas",
    "leche chocolatada", "queso untable", "crema de leche",
]

def _init_catalog_db():
    if not _DB_URL: return
    conn = _pg(); cur = conn.cursor()
    for sql in [
        "CREATE TABLE IF NOT EXISTS zonas (id SERIAL PRIMARY KEY, nombre TEXT UNIQUE NOT NULL, lat DOUBLE PRECISION NOT NULL, lng DOUBLE PRECISION NOT NULL, radio_km DOUBLE PRECISION NOT NULL DEFAULT 10.0)",
        "CREATE TABLE IF NOT EXISTS vtex_productos (id TEXT PRIMARY KEY, nombre TEXT NOT NULL, marca TEXT, presentacion TEXT, nombre_lower TEXT, marca_lower TEXT, cadena TEXT NOT NULL, precio DOUBLE PRECISION, precio_lista DOUBLE PRECISION, imagen TEXT, updated_at TIMESTAMP DEFAULT NOW())",
        "CREATE TABLE IF NOT EXISTS scraper_runs (id SERIAL PRIMARY KEY, zona_id INTEGER, ts TIMESTAMP DEFAULT NOW(), duration DOUBLE PRECISION, products INTEGER, prices INTEGER)",
        "CREATE TABLE IF NOT EXISTS promos_bancarias (id SERIAL PRIMARY KEY, cadena TEXT NOT NULL, banco TEXT NOT NULL, tarjeta TEXT, descuento_pct DOUBLE PRECISION NOT NULL, dia_semana TEXT, tope_reintegro DOUBLE PRECISION, vigencia_desde TEXT, vigencia_hasta TEXT, condiciones TEXT, updated_at TIMESTAMP DEFAULT NOW())",
        "CREATE INDEX IF NOT EXISTS idx_vp_nombre ON vtex_productos(nombre_lower)",
        "CREATE INDEX IF NOT EXISTS idx_vp_marca ON vtex_productos(marca_lower)",
        "CREATE INDEX IF NOT EXISTS idx_vp_cadena ON vtex_productos(cadena)",
        "CREATE INDEX IF NOT EXISTS idx_pb_cadena ON promos_bancarias(cadena)",
        # Smart Discount Optimizer tables
        "CREATE TABLE IF NOT EXISTS medios_pago (id SERIAL PRIMARY KEY, nombre TEXT NOT NULL, tipo TEXT NOT NULL, nombre_display TEXT NOT NULL, tarjetas_disponibles TEXT, activo BOOLEAN DEFAULT TRUE, updated_at TIMESTAMP DEFAULT NOW())",
        "ALTER TABLE promos_bancarias ADD COLUMN IF NOT EXISTS tipo TEXT DEFAULT 'banco'",
        "ALTER TABLE promos_bancarias ADD COLUMN IF NOT EXISTS medio_pago_id INTEGER",
        "CREATE INDEX IF NOT EXISTS idx_mp_tipo ON medios_pago(tipo)",
        "CREATE INDEX IF NOT EXISTS idx_pb_medio ON promos_bancarias(medio_pago_id)",
    ]:
        cur.execute(sql)
    cur.execute("INSERT INTO zonas (nombre,lat,lng,radio_km) VALUES ('Zona Sur GBA',-34.83,-58.39,12.0) ON CONFLICT (nombre) DO NOTHING")
    conn.commit(); cur.close(); conn.close()
    _seed_promos()
    _seed_medios_pago()


def _vtex_search(base_url, query, _from=0, _to=49):
    """Search products via VTEX API."""
    try:
        r = _requests.get(
            f"{base_url}/api/catalog_system/pub/products/search/{query}",
            params={"_from": _from, "_to": _to},
            headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"},
            timeout=15
        )
        if r.status_code in (200, 206):
            return r.json() if isinstance(r.json(), list) else []
    except: pass
    return []

def _run_catalog_scraper():
    if not _DB_URL: return
    import logging; log = logging.getLogger("scraper")
    log.info("=== VTEX Catalog scraper start ===")
    start = _time.time()
    total = 0

    for cadena, base_url in _VTEX_STORES.items():
        log.info(f"Scraping {cadena}...")
        store_count = 0
        for term in _SCRAPE_TERMS:
            products = _vtex_search(base_url, term)
            if not products:
                continue
            rows = []
            for p in products:
                pid = p.get("productId", "")
                if not pid: continue
                name = p.get("productName", "")
                brand = p.get("brand", "")
                # Get price from first item/seller
                price, list_price, image = 0.0, 0.0, ""
                try:
                    item = p["items"][0]
                    offer = item["sellers"][0]["commertialOffer"]
                    price = float(offer.get("Price", 0))
                    list_price = float(offer.get("ListPrice", price))
                    images = item.get("images", [])
                    image = images[0]["imageUrl"] if images else ""
                except: pass
                if price <= 0: continue
                # Filter out invalid prices (Jumbo/Disco API sometimes returns price per gram/ml)
                if price < 1000: continue
                # Extract presentacion from name or item
                presentacion = ""
                try:
                    presentacion = p["items"][0].get("measurementUnit", "") + " " + str(p["items"][0].get("unitMultiplier", ""))
                except: pass
                doc_id = f"{cadena}_{pid}"
                rows.append((doc_id, name, brand, presentacion.strip(), name.lower(), brand.lower(), cadena, price, list_price, image))
            if rows:
                cn = _pg(); cr = cn.cursor()
                execute_values(cr, """
                    INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen,updated_at)
                    VALUES %s ON CONFLICT (id) DO UPDATE SET
                    nombre=EXCLUDED.nombre,marca=EXCLUDED.marca,precio=EXCLUDED.precio,
                    precio_lista=EXCLUDED.precio_lista,imagen=EXCLUDED.imagen,
                    nombre_lower=EXCLUDED.nombre_lower,marca_lower=EXCLUDED.marca_lower,updated_at=NOW()
                """, rows)
                cn.commit(); cr.close(); cn.close()
                store_count += len(rows)
            _time.sleep(0.5)  # Rate limit
        total += store_count
        log.info(f"  {cadena}: {store_count} products")

    # Save run status
    cn = _pg(); cr = cn.cursor()
    cr.execute("INSERT INTO scraper_runs (duration,products,prices) VALUES (%s,%s,%s)", (_time.time()-start, total, total))
    cn.commit(); cr.close(); cn.close()

    # Also scrape Changomas via SEPA
    _scrape_changomas_sepa(total)

    log.info(f"=== VTEX scraper done: {total} products in {_time.time()-start:.0f}s ===")

def _scrape_changomas_sepa(vtex_total):
    """Scrape Changomas prices via SEPA API (they don't have VTEX)."""
    import logging; log = logging.getLogger("scraper")
    log.info("Scraping Changomas via SEPA...")
    count = 0
    for term in _SCRAPE_TERMS:
        try:
            r = _requests.get(f"{SEPA_BASE_URL}/productos",
                params={"string": term, "lat": "-34.83", "lng": "-58.39"},
                headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
            if r.status_code != 200: continue
            data = r.json()
            productos = data.get("productos", [])[:25]
            for p in productos:
                pid = p.get("id", "")
                if not pid: continue
                # Get prices at nearby Changomas
                pr = _requests.get(f"{SEPA_BASE_URL}/producto",
                    params={"id_producto": pid, "lat": "-34.83", "lng": "-58.39"},
                    headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
                if pr.status_code not in (200, 206): continue
                pdata = pr.json()
                rows = []
                for suc in pdata.get("sucursales", []):
                    cadena = suc.get("banderaDescripcion", "")
                    if "changomas" not in cadena.lower() and "walmart" not in cadena.lower():
                        continue
                    pp = suc.get("preciosProducto", {})
                    precio = pp.get("precioLista")
                    if not precio or float(precio) < 1000: continue
                    dist = suc.get("distanciaNumero", 999)
                    if dist > 15: continue
                    doc_id = f"Changomas_{pid}"
                    rows.append((doc_id, p.get("nombre", ""), p.get("marca", ""), p.get("presentacion", ""),
                        p.get("nombre", "").lower(), p.get("marca", "").lower(), "Changomas",
                        float(precio), float(precio), ""))
                if rows:
                    cn = _pg(); cr = cn.cursor()
                    execute_values(cr, """INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
                        VALUES %s ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio,updated_at=NOW()""", rows)
                    cn.commit(); cr.close(); cn.close()
                    count += len(rows)
                _time.sleep(0.3)
        except: pass
        _time.sleep(0.3)
    log.info(f"Changomas (SEPA): {count} products")

# Catalog API endpoints
@app.get("/catalog/productos")
def catalog_search(q: str = Query(..., min_length=2), marca: str = Query(None), cadena: str = Query(None), limit: int = Query(30, le=100)):
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    q_lower = q.lower().strip()
    words = q_lower.split()
    # Build WHERE with all words required (AND logic)
    conditions = ["nombre_lower LIKE %s" for _ in words]
    params = [f"%{w}%" for w in words]
    sql = f"SELECT id,nombre,marca,presentacion,cadena,precio,precio_lista,imagen FROM vtex_productos WHERE {' AND '.join(conditions)}"
    if marca:
        sql += " AND marca_lower LIKE %s"; params.append(f"%{marca.lower().strip()}%")
    if cadena:
        sql += " AND cadena = %s"; params.append(cadena)
    # Prioritize products where name starts with the search
    sql += f" ORDER BY CASE WHEN nombre_lower LIKE %s THEN 0 ELSE 1 END, precio ASC LIMIT %s"
    params.extend([f"{q_lower}%", limit])
    cr.execute(sql, params); rows = cr.fetchall(); cr.close(); cn.close()
    return [{"id":r[0],"nombre":r[1],"marca":r[2],"presentacion":r[3],"cadena":r[4],"precio":r[5],"precioLista":r[6],"imagen":r[7]} for r in rows]

@app.get("/catalog/cadenas")
def catalog_cadenas():
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT DISTINCT cadena FROM vtex_productos ORDER BY cadena"); rows = cr.fetchall(); cr.close(); cn.close()
    return [r[0] for r in rows if r[0]]

@app.get("/catalog/promos")
def catalog_promos(cadena: str = Query(None)):
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    if cadena:
        cr.execute("SELECT id,cadena,banco,tarjeta,descuento_pct,dia_semana,tope_reintegro,condiciones FROM promos_bancarias WHERE LOWER(cadena) LIKE %s ORDER BY descuento_pct DESC", (f"%{cadena.lower()}%",))
    else:
        cr.execute("SELECT id,cadena,banco,tarjeta,descuento_pct,dia_semana,tope_reintegro,condiciones FROM promos_bancarias ORDER BY cadena,descuento_pct DESC")
    rows = cr.fetchall(); cr.close(); cn.close()
    return [{"id":r[0],"cadena":r[1],"banco":r[2],"tarjeta":r[3],"descuentoPct":r[4],"diaSemana":r[5],"topeReintegro":r[6],"condiciones":r[7]} for r in rows]

@app.get("/catalog/marcas")
def catalog_marcas(q: str = Query(None)):
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    if q:
        cr.execute("SELECT DISTINCT marca FROM vtex_productos WHERE marca_lower LIKE %s ORDER BY marca LIMIT 20", (f"%{q.lower()}%",))
    else:
        cr.execute("SELECT DISTINCT marca FROM vtex_productos ORDER BY marca LIMIT 50")
    rows = cr.fetchall(); cr.close(); cn.close()
    return [r[0] for r in rows if r[0]]

@app.get("/catalog/status")
def catalog_status():
    if not _DB_URL: return {"lastRun":"","totalProducts":0}
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT ts,duration,products FROM scraper_runs ORDER BY ts DESC LIMIT 1"); row = cr.fetchone()
    cr.execute("SELECT COUNT(*) FROM vtex_productos"); tp = cr.fetchone()[0]
    cr.execute("SELECT COUNT(DISTINCT cadena) FROM vtex_productos"); nc = cr.fetchone()[0]
    cr.close(); cn.close()
    return {"lastRun":str(row[0]) if row else "","totalProducts":tp,"totalCadenas":nc}

@app.post("/catalog/scrape")
def catalog_trigger_scrape():
    threading.Thread(target=_run_catalog_scraper, daemon=True).start()
    return {"status":"started"}

# --- Smart Discount Optimizer endpoints ---

@app.get("/catalog/medios_pago")
def catalog_medios_pago():
    """Return master list of payment methods grouped by type."""
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT id,nombre,tipo,nombre_display,tarjetas_disponibles,activo FROM medios_pago WHERE activo=TRUE ORDER BY tipo,nombre")
    rows = cr.fetchall(); cr.close(); cn.close()
    return [{"id":r[0],"nombre":r[1],"tipo":r[2],"nombreDisplay":r[3],
             "tarjetasDisponibles":r[4].split(",") if r[4] else None,"activo":r[5]} for r in rows]

@app.get("/catalog/buscar_opciones")
def catalog_buscar_opciones(q: str = Query(..., min_length=2)):
    """Search product options by name, grouped by name+brand+presentacion with prices per chain."""
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    q_lower = q.lower().strip()
    # Search with all words required (AND logic) and prioritize exact matches
    words = q_lower.split()
    if len(words) > 1:
        # Build WHERE with all words required
        conditions = " AND ".join(["nombre_lower LIKE %s"] * len(words))
        params = [f"%{w}%" for w in words]
        # First try: products where name starts with the search term (most relevant)
        cr.execute(f"""
            SELECT nombre,marca,presentacion,cadena,id,precio FROM vtex_productos
            WHERE {conditions}
            ORDER BY
                CASE WHEN nombre_lower LIKE %s THEN 0 ELSE 1 END,
                precio ASC
            LIMIT 200
        """, params + [f"{q_lower}%"])
    else:
        cr.execute("""
            SELECT nombre,marca,presentacion,cadena,id,precio FROM vtex_productos
            WHERE nombre_lower LIKE %s
            ORDER BY
                CASE WHEN nombre_lower LIKE %s THEN 0 ELSE 1 END,
                precio ASC
            LIMIT 200
        """, (f"%{q_lower}%", f"{q_lower}%"))
    rows = cr.fetchall(); cr.close(); cn.close()
    # Group by nombre+marca
    from collections import OrderedDict
    groups = OrderedDict()
    for nombre,marca,pres,cadena,pid,precio in rows:
        key = f"{nombre}|{marca}"
        if key not in groups:
            groups[key] = {"nombre":nombre,"marca":marca,"presentacion":pres,"preciosPorCadena":[]}
        groups[key]["preciosPorCadena"].append({"cadena":cadena,"productoId":pid,"precio":precio})
    return list(groups.values())[:50]

@app.post("/catalog/optimizar")
def catalog_optimizar(body: dict):
    """Calculate optimal supermarket + payment split for a shopping list."""
    if not _DB_URL: return {"error":"DB not configured"}
    productos_req = body.get("productos", [])
    medios_ids = body.get("medios_pago_ids", [])
    tarjetas_sel = body.get("tarjetas_seleccionadas", {})
    dia = body.get("dia_semana", "")

    cn = _pg(); cr = cn.cursor()

    # Get all chains
    cr.execute("SELECT DISTINCT cadena FROM vtex_productos")
    cadenas = [r[0] for r in cr.fetchall()]

    # Get applicable promos
    promo_sql = "SELECT cadena,banco,tarjeta,descuento_pct,dia_semana,tope_reintegro,medio_pago_id FROM promos_bancarias WHERE 1=1"
    promo_params = []
    if medios_ids:
        promo_sql += " AND medio_pago_id IN %s"
        promo_params.append(tuple(medios_ids))
    cr.execute(promo_sql, promo_params)
    all_promos = cr.fetchall()

    # Filter promos by day
    def promo_applies(promo_dia):
        if not promo_dia or not dia: return True
        return promo_dia.lower() == dia.lower()

    ranking = []
    for cadena in cadenas:
        total = 0.0
        missing = []
        selected_products = []  # Track which products were selected
        for prod in productos_req:
            pid = prod.get("producto_id")
            nombre = prod.get("nombre", "")
            qty = prod.get("cantidad", 1)
            if pid and pid != "cualquier_marca":
                cr.execute("SELECT nombre,marca,precio FROM vtex_productos WHERE id=%s", (pid,))
                row = cr.fetchone()
                if row:
                    total += row[2] * qty
                    selected_products.append({"nombre": row[0], "marca": row[1], "precio": row[2], "cantidad": qty, "busqueda": nombre})
                else:
                    missing.append(pid)
            else:
                # cualquier_marca: find cheapest in this chain
                nombre_lower = nombre.lower().strip()
                found = False
                for search in [nombre_lower, nombre_lower.split()[0] if nombre_lower else ""]:
                    if not search: continue
                    cr.execute("SELECT nombre,marca,precio FROM vtex_productos WHERE cadena=%s AND nombre_lower LIKE %s ORDER BY precio LIMIT 1",
                        (cadena, f"%{search}%"))
                    row = cr.fetchone()
                    if row:
                        total += row[2] * qty
                        selected_products.append({"nombre": row[0], "marca": row[1], "precio": row[2], "cantidad": qty, "busqueda": nombre})
                        found = True
                        break
                if not found:
                    missing.append(nombre)

        if total <= 0: continue

        # Apply discounts (greedy)
        cadena_promos = [(p[1],p[2],p[3],p[4],p[5]) for p in all_promos if p[0]==cadena and promo_applies(p[4])]
        cadena_promos.sort(key=lambda x: -x[2])  # Sort by descuento_pct DESC

        remaining = total
        pagos = []
        for banco,tarjeta,dpct,dia_p,tope in cadena_promos:
            if remaining <= 0: break
            descuento = remaining * (dpct / 100)
            tope_val = tope or 0
            if tope_val > 0 and descuento > tope_val:
                descuento = tope_val
                monto = tope_val / (dpct / 100)
            else:
                monto = remaining
            pagos.append({"medioPago":banco,"tarjeta":tarjeta,"monto":round(monto,2),
                "descuentoPct":dpct,"ahorro":round(descuento,2),"topeAplicado":tope_val>0 and descuento>=tope_val})
            remaining -= monto

        if remaining > 0:
            pagos.append({"medioPago":"Efectivo","tarjeta":None,"monto":round(remaining,2),
                "descuentoPct":0,"ahorro":0,"topeAplicado":False})

        ahorro = sum(p["ahorro"] for p in pagos)
        final = total - ahorro
        ranking.append({
            "cadena":cadena,"totalOriginal":round(total,2),"totalFinal":round(final,2),
            "ahorro":round(ahorro,2),"ahorroPorcentaje":round(ahorro/total*100,2) if total>0 else 0,
            "distribucionPagos":pagos,"productosFaltantes":missing,
            "productosSeleccionados":selected_products
        })

    cr.close(); cn.close()

    ranking.sort(key=lambda x: x["totalFinal"])
    best = ranking[0] if ranking else None

    return {
        "cadenaRecomendada": best["cadena"] if best else None,
        "totalOriginal": best["totalOriginal"] if best else 0,
        "totalFinal": best["totalFinal"] if best else 0,
        "ahorroTotal": best["ahorro"] if best else 0,
        "ahorroPorcentaje": best["ahorroPorcentaje"] if best else 0,
        "distribucionPagos": best["distribucionPagos"] if best else [],
        "productosFaltantes": best["productosFaltantes"] if best else [],
        "productosSeleccionados": best["productosSeleccionados"] if best else [],
        "rankingCadenas": [{"cadena":r["cadena"],"totalFinal":r["totalFinal"],"ahorro":r["ahorro"]} for r in ranking]
    }

# Remove old endpoints that are no longer needed
# /catalog/precios, /catalog/localidades, /catalog/zonas are removed
# Products now include price directly from VTEX

@app.on_event("startup")
def _on_startup():
    if _DB_URL:
        _init_catalog_db()
        threading.Thread(target=_run_catalog_scraper, daemon=True).start()
        from apscheduler.schedulers.background import BackgroundScheduler
        sched = BackgroundScheduler()
        sched.add_job(_run_catalog_scraper, "cron", hour=6, minute=0)
        sched.start()
