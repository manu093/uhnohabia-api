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


# ─── Precios Claros Catalog (Scraper + Postgres/Neon) ─────────────────────────
import psycopg2
from psycopg2.extras import execute_values
import threading, time as _time, requests as _requests

_DB_URL = os.environ.get("DATABASE_URL", "")

def _pg():
    return psycopg2.connect(_DB_URL) if _DB_URL else None

def _init_catalog_db():
    if not _DB_URL:
        return
    conn = _pg()
    cur = conn.cursor()
    for sql in [
        """CREATE TABLE IF NOT EXISTS zonas (id SERIAL PRIMARY KEY, nombre TEXT UNIQUE NOT NULL, lat DOUBLE PRECISION NOT NULL, lng DOUBLE PRECISION NOT NULL, radio_km DOUBLE PRECISION NOT NULL DEFAULT 10.0)""",
        """CREATE TABLE IF NOT EXISTS cat_sucursales (id TEXT PRIMARY KEY, cadena TEXT NOT NULL, nombre TEXT, direccion TEXT, localidad TEXT, provincia TEXT, lat TEXT, lng TEXT, zona_id INTEGER REFERENCES zonas(id), updated_at TIMESTAMP DEFAULT NOW())""",
        """CREATE TABLE IF NOT EXISTS cat_productos (id TEXT PRIMARY KEY, nombre TEXT NOT NULL, marca TEXT, presentacion TEXT, nombre_lower TEXT, marca_lower TEXT, updated_at TIMESTAMP DEFAULT NOW())""",
        """CREATE TABLE IF NOT EXISTS cat_precios (id TEXT PRIMARY KEY, producto_id TEXT REFERENCES cat_productos(id) ON DELETE CASCADE, sucursal_id TEXT REFERENCES cat_sucursales(id) ON DELETE CASCADE, producto_nombre TEXT, cadena TEXT, precio DOUBLE PRECISION NOT NULL, fecha TEXT, updated_at TIMESTAMP DEFAULT NOW())""",
        """CREATE TABLE IF NOT EXISTS scraper_runs (id SERIAL PRIMARY KEY, zona_id INTEGER, ts TIMESTAMP DEFAULT NOW(), duration DOUBLE PRECISION, products INTEGER, prices INTEGER)""",
        "CREATE INDEX IF NOT EXISTS idx_cp_nombre ON cat_productos(nombre_lower)",
        "CREATE INDEX IF NOT EXISTS idx_cp_marca ON cat_productos(marca_lower)",
        "CREATE INDEX IF NOT EXISTS idx_cpr_prod ON cat_precios(producto_id)",
        "CREATE INDEX IF NOT EXISTS idx_cpr_suc ON cat_precios(sucursal_id)",
        "CREATE INDEX IF NOT EXISTS idx_cpr_cadena ON cat_precios(cadena)",
        "CREATE INDEX IF NOT EXISTS idx_cs_zona ON cat_sucursales(zona_id)",
        "CREATE INDEX IF NOT EXISTS idx_cs_loc ON cat_sucursales(localidad)",
    ]:
        cur.execute(sql)
    cur.execute("INSERT INTO zonas (nombre,lat,lng,radio_km) VALUES ('Zona Sur GBA',-34.83,-58.39,12.0) ON CONFLICT (nombre) DO NOTHING")
    conn.commit(); cur.close(); conn.close()

_SCRAPE_TERMS = [
    "leche","arroz","fideos","aceite","harina","azucar","yerba","cafe","te",
    "galletitas","pan","manteca","queso","yogur","huevos","pollo","carne",
    "jamon","salchichas","atun","tomate","mayonesa","mostaza","ketchup","sal",
    "cerveza","vino","gaseosa","agua","jugo","detergente","jabon","shampoo",
    "papel higienico","lavandina","desodorante","pasta dental","pañales",
    "pure de tomate","mermelada","dulce de leche","cereales","avena","polenta","lentejas",
]

def _sepa_get(url, params=None):
    for attempt in range(3):
        try:
            r = _requests.get(url, params=params, headers={"User-Agent":"UhNoHabia/1.0"}, timeout=15)
            if r.status_code == 200: return r.json()
        except: pass
        _time.sleep(1*(attempt+1))
    return None

def _run_catalog_scraper():
    if not _DB_URL: return
    import logging; log = logging.getLogger("scraper"); log.info("=== Catalog scraper start ===")
    start = _time.time()
    conn = _pg(); cur = conn.cursor()
    cur.execute("SELECT id,nombre,lat,lng,radio_km FROM zonas"); zonas = cur.fetchall()
    cur.close(); conn.close()
    tp, tpr = 0, 0
    for zid, zname, zlat, zlng, zrad in zonas:
        # Paginated sucursales
        srows, offset = [], 0
        while True:
            d = _sepa_get(f"{SEPA_BASE_URL}/sucursales", {"lat":str(zlat),"lng":str(zlng),"offset":str(offset),"limit":"30"})
            if not d or not d.get("sucursales"): break
            for s in d["sucursales"]:
                sid,slat,slng2 = s.get("id",""),s.get("lat",""),s.get("lng","")
                if not sid or not slat or not slng2: continue
                try: dist = haversine_km(zlat,zlng,float(slat),float(slng2))
                except: continue
                if dist <= zrad:
                    srows.append((sid,s.get("banderaDescripcion",s.get("comercioRazonSocial","")),s.get("sucursalNombre",""),s.get("direccion",""),s.get("localidad",""),s.get("provincia",""),slat,slng2,zid))
            total = d.get("total",0); offset += 30
            if offset >= total: break
            _time.sleep(0.3)
        if srows:
            cn = _pg(); cr = cn.cursor()
            execute_values(cr, "INSERT INTO cat_sucursales (id,cadena,nombre,direccion,localidad,provincia,lat,lng,zona_id) VALUES %s ON CONFLICT (id) DO UPDATE SET cadena=EXCLUDED.cadena,nombre=EXCLUDED.nombre,direccion=EXCLUDED.direccion,localidad=EXCLUDED.localidad,updated_at=NOW()", srows)
            cn.commit(); cr.close(); cn.close()
        log.info(f"Zone {zname}: {len(srows)} sucursales")
        # Products + prices
        for term in _SCRAPE_TERMS:
            pd = _sepa_get(f"{SEPA_BASE_URL}/productos", {"string":term,"lat":str(zlat),"lng":str(zlng)})
            if not pd or not pd.get("productos"): continue
            prods = pd["productos"][:25]
            prows = [(p["id"],p.get("nombre",""),p.get("marca",""),p.get("presentacion",""),p.get("nombre","").lower(),p.get("marca","").lower()) for p in prods if p.get("id")]
            if prows:
                cn = _pg(); cr = cn.cursor()
                execute_values(cr, "INSERT INTO cat_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower) VALUES %s ON CONFLICT (id) DO UPDATE SET nombre=EXCLUDED.nombre,marca=EXCLUDED.marca,presentacion=EXCLUDED.presentacion,nombre_lower=EXCLUDED.nombre_lower,marca_lower=EXCLUDED.marca_lower,updated_at=NOW()", prows)
                cn.commit(); cr.close(); cn.close()
                tp += len(prows)
            for p in prods:
                pid = p.get("id","")
                if not pid: continue
                prd = _sepa_get(f"{SEPA_BASE_URL}/producto", {"id_producto":pid,"lat":str(zlat),"lng":str(zlng)})
                if not prd or not prd.get("sucursales"): continue
                price_rows = []
                for sc in prd["sucursales"]:
                    pp = sc.get("preciosProducto",{})
                    pv = pp.get("precioLista")
                    if not pv: continue
                    dist2 = sc.get("distanciaNumero",999)
                    if dist2 > zrad: continue
                    fsid = f"{sc.get('comercioId','')}-{sc.get('banderaId','')}-{sc.get('id','')}"
                    # Ensure sucursal exists
                    cn = _pg(); cr = cn.cursor()
                    cr.execute("INSERT INTO cat_sucursales (id,cadena,nombre,direccion,localidad,provincia,lat,lng,zona_id) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s) ON CONFLICT (id) DO NOTHING",
                        (fsid,sc.get("banderaDescripcion",""),sc.get("sucursalNombre",""),sc.get("direccion",""),sc.get("localidad",""),sc.get("provincia",""),sc.get("lat",""),sc.get("lng",""),zid))
                    cn.commit(); cr.close(); cn.close()
                    price_rows.append((f"{pid}_{fsid}",pid,fsid,p.get("nombre",""),sc.get("banderaDescripcion",""),float(pv),""))
                if price_rows:
                    cn = _pg(); cr = cn.cursor()
                    execute_values(cr, "INSERT INTO cat_precios (id,producto_id,sucursal_id,producto_nombre,cadena,precio,fecha) VALUES %s ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio,cadena=EXCLUDED.cadena,updated_at=NOW()", price_rows)
                    cn.commit(); cr.close(); cn.close()
                    tpr += len(price_rows)
                _time.sleep(0.3)
            _time.sleep(0.3)
        cn = _pg(); cr = cn.cursor()
        cr.execute("INSERT INTO scraper_runs (zona_id,duration,products,prices) VALUES (%s,%s,%s,%s)", (zid,_time.time()-start,tp,tpr))
        cn.commit(); cr.close(); cn.close()
    log.info(f"=== Catalog scraper done: {tp} products, {tpr} prices in {_time.time()-start:.0f}s ===")

# Catalog API endpoints
@app.get("/catalog/productos")
def catalog_search(q: str = Query(..., min_length=2), marca: str = Query(None), limit: int = Query(30, le=100)):
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    ql = f"%{q.lower().strip()}%"
    if marca:
        cr.execute("SELECT id,nombre,marca,presentacion FROM cat_productos WHERE nombre_lower LIKE %s AND marca_lower LIKE %s ORDER BY nombre LIMIT %s", (ql,f"%{marca.lower().strip()}%",limit))
    else:
        cr.execute("SELECT id,nombre,marca,presentacion FROM cat_productos WHERE nombre_lower LIKE %s ORDER BY nombre LIMIT %s", (ql,limit))
    rows = cr.fetchall(); cr.close(); cn.close()
    return [{"id":r[0],"nombre":r[1],"marca":r[2],"presentacion":r[3]} for r in rows]

@app.get("/catalog/precios/{producto_id}")
def catalog_prices(producto_id: str, cadena: str = Query(None), localidad: str = Query(None)):
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    sql = "SELECT p.id,p.producto_id,p.sucursal_id,p.producto_nombre,p.cadena,p.precio,p.fecha,s.nombre,s.direccion,s.localidad FROM cat_precios p JOIN cat_sucursales s ON p.sucursal_id=s.id WHERE p.producto_id=%s"
    params = [producto_id]
    if cadena: sql += " AND LOWER(p.cadena) LIKE %s"; params.append(f"%{cadena.lower()}%")
    if localidad: sql += " AND LOWER(s.localidad) LIKE %s"; params.append(f"%{localidad.lower()}%")
    sql += " ORDER BY p.precio ASC LIMIT 100"
    cr.execute(sql, params); rows = cr.fetchall(); cr.close(); cn.close()
    return [{"id":r[0],"productoId":r[1],"sucursalId":r[2],"productoNombre":r[3],"cadena":r[4],"precio":r[5],"fecha":r[6],"sucursalNombre":r[7],"direccion":r[8],"localidad":r[9]} for r in rows]

@app.get("/catalog/cadenas")
def catalog_cadenas():
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT DISTINCT cadena FROM cat_sucursales ORDER BY cadena"); rows = cr.fetchall(); cr.close(); cn.close()
    return [r[0] for r in rows if r[0]]

@app.get("/catalog/localidades")
def catalog_localidades():
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT DISTINCT localidad FROM cat_sucursales ORDER BY localidad"); rows = cr.fetchall(); cr.close(); cn.close()
    return [r[0] for r in rows if r[0]]

@app.get("/catalog/zonas")
def catalog_zonas():
    if not _DB_URL: return []
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT id,nombre,lat,lng,radio_km FROM zonas ORDER BY nombre"); rows = cr.fetchall(); cr.close(); cn.close()
    return [{"id":r[0],"nombre":r[1],"lat":r[2],"lng":r[3],"radioKm":r[4]} for r in rows]

@app.get("/catalog/status")
def catalog_status():
    if not _DB_URL: return {"lastRun":"","totalProducts":0,"totalPrices":0,"totalSucursales":0}
    cn = _pg(); cr = cn.cursor()
    cr.execute("SELECT ts,duration,products,prices FROM scraper_runs ORDER BY ts DESC LIMIT 1"); row = cr.fetchone()
    cr.execute("SELECT COUNT(*) FROM cat_productos"); tp = cr.fetchone()[0]
    cr.execute("SELECT COUNT(*) FROM cat_precios"); tpr = cr.fetchone()[0]
    cr.execute("SELECT COUNT(*) FROM cat_sucursales"); ts = cr.fetchone()[0]
    cr.close(); cn.close()
    return {"lastRun":str(row[0]) if row else "","totalProducts":tp,"totalPrices":tpr,"totalSucursales":ts}

@app.post("/catalog/scrape")
def catalog_trigger_scrape():
    threading.Thread(target=_run_catalog_scraper, daemon=True).start()
    return {"status":"started"}

# Init DB + schedule scraper on startup
@app.on_event("startup")
def _on_startup():
    if _DB_URL:
        _init_catalog_db()
        threading.Thread(target=_run_catalog_scraper, daemon=True).start()
        from apscheduler.schedulers.background import BackgroundScheduler
        sched = BackgroundScheduler()
        sched.add_job(_run_catalog_scraper, "cron", hour=6, minute=0)
        sched.start()
