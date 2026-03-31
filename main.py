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
        "CREATE INDEX IF NOT EXISTS idx_vp_nombre ON vtex_productos(nombre_lower)",
        "CREATE INDEX IF NOT EXISTS idx_vp_marca ON vtex_productos(marca_lower)",
        "CREATE INDEX IF NOT EXISTS idx_vp_cadena ON vtex_productos(cadena)",
    ]:
        cur.execute(sql)
    cur.execute("INSERT INTO zonas (nombre,lat,lng,radio_km) VALUES ('Zona Sur GBA',-34.83,-58.39,12.0) ON CONFLICT (nombre) DO NOTHING")
    conn.commit(); cur.close(); conn.close()


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
    log.info(f"=== VTEX scraper done: {total} products in {_time.time()-start:.0f}s ===")

# Catalog API endpoints
@app.get("/catalog/productos")
def catalog_search(q: str = Query(..., min_length=2), marca: str = Query(None), cadena: str = Query(None), limit: int = Query(30, le=100)):
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    sql = "SELECT id,nombre,marca,presentacion,cadena,precio,precio_lista,imagen FROM vtex_productos WHERE nombre_lower LIKE %s"
    params = [f"%{q.lower().strip()}%"]
    if marca:
        sql += " AND marca_lower LIKE %s"; params.append(f"%{marca.lower().strip()}%")
    if cadena:
        sql += " AND cadena = %s"; params.append(cadena)
    sql += " ORDER BY precio ASC LIMIT %s"; params.append(limit)
    cr.execute(sql, params); rows = cr.fetchall(); cr.close(); cn.close()
    return [{"id":r[0],"nombre":r[1],"marca":r[2],"presentacion":r[3],"cadena":r[4],"precio":r[5],"precioLista":r[6],"imagen":r[7]} for r in rows]

@app.get("/catalog/cadenas")
def catalog_cadenas():
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT DISTINCT cadena FROM vtex_productos ORDER BY cadena"); rows = cr.fetchall(); cr.close(); cn.close()
    return [r[0] for r in rows if r[0]]

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
