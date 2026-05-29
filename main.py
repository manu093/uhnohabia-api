"""
Backend de precios para Listas de Compras Compartidas.
Consulta SEPA/Precios Claros y expone una API limpia para la app Android.
"""

from fastapi import FastAPI, Query
from fastapi.responses import HTMLResponse
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



@app.post("/catalog/import_products")
def catalog_import_products(body: dict):
    """Import products from external scraper (Raspberry Pi)."""
    api_key = body.get("api_key", "")
    if api_key != os.environ.get("SCRAPER_KEY", "uhnohabia-scraper-2026"):
        return {"error": "Unauthorized"}
    if not _DB_URL: return {"error": "DB not configured"}
    products = body.get("products", [])
    if not products: return {"imported": 0}
    cn = _pg(); cr = cn.cursor()
    count = 0
    for p in products:
        try:
            from psycopg2.extras import execute_values
            execute_values(cr, """INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
                VALUES %s ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio,cadena=EXCLUDED.cadena,nombre=EXCLUDED.nombre,marca=EXCLUDED.marca,updated_at=NOW()""",
                [(p["id"], p["nombre"], p["marca"], p.get("presentacion",""), p["nombre"].lower(), p["marca"].lower(), p["cadena"], p["precio"], p["precio"], "")])
            count += 1
        except Exception as e:
            cn.rollback()
            continue
    cn.commit(); cr.close(); cn.close()
    return {"imported": count}


@app.get("/privacy", response_class=HTMLResponse)
async def privacy_policy():
    return "<html><head><meta charset=utf-8><meta name=viewport content=width=device-width,initial-scale=1><title>Privacidad - Uh No Habia</title><style>body{font-family:system-ui;max-width:700px;margin:40px auto;padding:0 20px;line-height:1.6}h1{color:#FF6B6B}h2{color:#4ECDC4}</style></head><body><h1>Politica de Privacidad</h1><p><b>Uh No Habia</b> - Abril 2026</p><h2>Datos que recopilamos</h2><ul><li>Email y nombre para autenticacion</li><li>Listas de compras y productos</li><li>Preferencias de tema y medios de pago</li><li>Comandos de voz via Alexa</li></ul><h2>Uso de datos</h2><ul><li>Sincronizar listas entre dispositivos</li><li>Compartir listas con otros usuarios</li><li>Optimizar precios via Precios Claros (SEPA)</li><li>Procesar comandos de voz</li></ul><h2>Almacenamiento</h2><ul><li>Firebase Firestore (Google Cloud)</li><li>Base de datos local en tu dispositivo</li><li>No vendemos ni compartimos datos con terceros</li></ul><h2>Alexa</h2><ul><li>Amazon procesa tu voz y nos envia el texto</li><li>Solo recibimos el nombre del producto</li><li>No almacenamos grabaciones de voz</li></ul><h2>Tus derechos</h2><ul><li>Podes eliminar tu cuenta y datos desde la app</li><li>Podes exportar listas como texto o CSV</li><li>Podes desvincular Alexa desde la app de Amazon</li></ul><h2>Contacto</h2><p>uhnohabia@gmail.com</p></body></html>"

@app.get("/terms", response_class=HTMLResponse)
async def terms_of_use():
    return "<html><head><meta charset=utf-8><meta name=viewport content=width=device-width,initial-scale=1><title>Terminos - Uh No Habia</title><style>body{font-family:system-ui;max-width:700px;margin:40px auto;padding:0 20px;line-height:1.6}h1{color:#FF6B6B}h2{color:#4ECDC4}</style></head><body><h1>Terminos de Uso</h1><p><b>Uh No Habia</b> - Abril 2026</p><h2>Uso</h2><p>App gratuita para listas de compras compartidas y comparar precios en Argentina.</p><h2>Precios</h2><p>Los precios provienen de Precios Claros (SEPA). No garantizamos exactitud.</p><h2>Cuenta</h2><p>Necesitas email para usar la app.</p><h2>Listas compartidas</h2><p>Al compartir, otros pueden ver y modificar productos.</p><h2>Contacto</h2><p>uhnohabia@gmail.com</p></body></html>"

@app.get("/app/version")
async def app_version():
    """Returns current app version info for OTA updates."""
    return {
        "versionCode": 9,
        "versionName": "1.5.1",
        "apkUrl": "https://github.com/manu093/uhnohabia-api/releases/download/v1.5.1/UhNoHabia.apk",
        "releaseNotes": "Fix tema Moderno: mejor busqueda de imagenes, cantidad en badge, checkbox mas visible",
        "forceUpdate": False
    }

@app.get("/app/download")
async def app_download():
    """Redirect to APK download URL."""
    from fastapi.responses import RedirectResponse
    return RedirectResponse("https://github.com/manu093/uhnohabia-api/releases/download/v1.5.1/UhNoHabia.apk")

@app.get("/health")
async def health():
    return {"status": "ok"}


# ─── Voice Command Processing (replaces Firebase Cloud Functions) ─────────────

import firebase_admin
from firebase_admin import credentials, firestore, auth as firebase_auth
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

# Simple token store: persisted in DB to survive restarts
_token_store: dict[str, str] = {}

def _init_token_store():
    """Create token table and load existing tokens."""
    if not _DB_URL: return
    try:
        cn = psycopg2.connect(_DB_URL); cr = cn.cursor()
        cr.execute("CREATE TABLE IF NOT EXISTS alexa_tokens (token TEXT PRIMARY KEY, uid TEXT NOT NULL, created_at TIMESTAMP DEFAULT NOW())")
        cn.commit()
        cr.execute("SELECT token, uid FROM alexa_tokens")
        for row in cr.fetchall():
            _token_store[row[0]] = row[1]
        cr.close(); cn.close()
    except: pass

def _save_token(token: str, uid: str):
    _token_store[token] = uid
    if not _DB_URL: return
    try:
        cn = psycopg2.connect(_DB_URL); cr = cn.cursor()
        cr.execute("INSERT INTO alexa_tokens (token, uid) VALUES (%s, %s) ON CONFLICT (token) DO UPDATE SET uid=EXCLUDED.uid", (token, uid))
        cn.commit(); cr.close(); cn.close()
    except: pass

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
        # Strategy 1: Use Firebase Admin SDK to verify user exists, then verify password via REST API
        # Strategy 2 (fallback): Use Firebase Admin to get user by email and create a custom token
        uid = None
        
        # Try REST API first (works if API key is not restricted)
        api_key = os.environ.get("FIREBASE_API_KEY", "AIzaSyA0ZD7YqjNV0Uu7cFg35wFQ8kKQRtFTHkw")
        try:
            async with httpx.AsyncClient() as client:
                r = await client.post(
                    f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}",
                    json={"email": email, "password": password, "returnSecureToken": True},
                    timeout=10
                )
                if r.status_code == 200:
                    uid = r.json()["localId"]
        except: pass

        # Fallback: verify with Firebase Admin SDK (sign in via REST with different approach)
        if not uid and _firebase_initialized:
            try:
                # Use a second API key approach or verify password hash
                # Firebase Admin can't verify passwords directly, but we can use the REST API
                # with the web API key from the Firebase project
                import requests as _req
                r2 = _req.post(
                    f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}",
                    json={"email": email, "password": password, "returnSecureToken": True},
                    timeout=10
                )
                if r2.status_code == 200:
                    uid = r2.json()["localId"]
                else:
                    err = r2.json().get("error", {}).get("message", "UNKNOWN")
                    return HTMLResponse(f"<h2>Error: credenciales inválidas</h2><p>{err}</p><a href='javascript:history.back()'>Volver</a>")
            except Exception as ex:
                return HTMLResponse(f"<h2>Error: {ex}</h2><a href='javascript:history.back()'>Volver</a>")

        if not uid:
            return HTMLResponse("<h2>Error: no se pudo verificar las credenciales</h2><a href='javascript:history.back()'>Volver</a>")

        token = uuid.uuid4().hex
        _save_token(token, uid)

        # Redirect back to Alexa with token in URL fragment
        # Format: redirect_uri#state=xyz&access_token=token&token_type=Bearer
        fragment = f"state={state}&access_token={token}&token_type=Bearer"
        redirect_url = f"{redirect_uri}#{fragment}"
        return HTMLResponse(
            f'<html><body><script>window.location.replace("{redirect_url}");</script></body></html>',
            status_code=200
        )
    except Exception as e:
        return HTMLResponse(f"<h2>Error: {e}</h2><a href='javascript:history.back()'>Volver</a>")


@app.post("/auth/admin-reset")
async def admin_reset_password(body: dict):
    # Basic auth check
    admin_key = body.get("admin_key", "")
    if admin_key != os.environ.get("ADMIN_KEY", "uhnohabia-admin-2026"):
        return {"error": "Unauthorized"}
    """Reset a user's password using Firebase Admin SDK."""
    if not _firebase_initialized:
        return {"error": "Firebase not initialized"}
    email = body.get("email", "")
    new_password = body.get("newPassword", "")
    if not email or not new_password or len(new_password) < 6:
        return {"error": "Email and password (min 6 chars) required"}
    try:
        user = firebase_auth.get_user_by_email(email)
        firebase_auth.update_user(user.uid, password=new_password)
        return {"ok": True, "message": f"Password reset for {email}"}
    except Exception as e:
        return {"error": str(e)}


def get_uid_from_alexa_request(body: dict) -> str:
    """Extract Firebase UID from Alexa request using the access token."""
    token = body.get("session", {}).get("user", {}).get("accessToken", "")
    if token and token in _token_store:
        return _token_store[token]
    # No valid token - return empty (will fail gracefully)
    return ""


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

# Minimum valid price per chain (Jumbo/Disco return price-per-unit instead of total price)
_MIN_PRICE = {"Jumbo": 1000, "Disco": 1000}
_DEFAULT_MIN_PRICE = 100

def _min_price_for(cadena):
    return _MIN_PRICE.get(cadena, _DEFAULT_MIN_PRICE)

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
    # Lácteos
    "leche entera", "leche descremada", "leche larga vida", "leche chocolatada",
    "leche UAT", "leche deslactosada", "crema de leche", "queso cremoso",
    "queso rallado", "queso untable", "queso crema", "queso barra",
    "queso port salut", "queso sardo", "queso pategras", "queso dambo",
    "yogur natural", "yogur frutilla", "yogur bebible", "yogur griego",
    "manteca", "dulce de leche", "ricotta", "leche en polvo",
    # Almacén
    "arroz largo fino", "arroz integral", "arroz doble carolina", "arroz parboil",
    "fideos spaghetti", "fideos tirabuzón", "fideos mostachol", "fideos tallarín",
    "fideos coditos", "fideos municiones", "harina 000", "harina leudante",
    "azúcar", "sal fina", "sal gruesa", "aceite girasol", "aceite oliva",
    "aceite mezcla", "vinagre", "yerba mate", "café instantáneo", "café molido",
    "té en saquitos", "polenta", "avena", "lentejas", "garbanzos", "porotos",
    "puré de tomate", "tomate perita", "salsa de tomate", "choclo en lata",
    "arvejas en lata", "atún en lata", "mermelada", "miel", "cereales",
    "galletitas dulces", "galletitas saladas", "pan lactal", "pan integral",
    "pan rallado", "rebozador", "caldo en cubo", "gelatina", "flan",
    "premezcla", "levadura", "maicena", "tapas empanadas", "tapas pascualina",
    "tapas tarta", "semillas", "frutos secos", "almendras", "nueces",
    "pasas de uva", "coco rallado", "chocolate", "cacao", "dulce batata",
    "dulce membrillo", "alfajor", "budín", "bizcochuelo",
    # Condimentos y aderezos
    "mayonesa", "mostaza", "ketchup", "salsa golf", "salsa soja",
    "pimienta", "pimentón", "orégano", "provenzal", "chimichurri",
    "ají molido", "comino", "cúrcuma", "nuez moscada", "laurel",
    "aceto balsámico", "salsa barbacoa", "aderezo caesar",
    # Carnes y fiambres
    "carne picada", "nalga", "bife", "asado", "pollo entero", "pechuga pollo",
    "pata muslo", "milanesa", "hamburguesa", "salchichas", "chorizo",
    "jamón cocido", "jamón crudo", "salame", "mortadela", "bondiola",
    "panceta", "paté", "morcilla", "matambre",
    # Frutas y verduras
    "manzana", "banana", "naranja", "mandarina", "limón", "pera",
    "durazno", "uva", "frutilla", "kiwi", "ananá", "sandía", "melón",
    "tomate", "lechuga", "cebolla", "papa", "zanahoria", "zapallo",
    "zapallito", "berenjena", "morrón", "pepino", "espinaca", "acelga",
    "brócoli", "coliflor", "choclo", "batata", "remolacha", "ajo",
    "perejil", "albahaca", "rúcula",
    # Bebidas
    "agua mineral", "gaseosa cola", "gaseosa lima", "gaseosa naranja",
    "jugo naranja", "jugo manzana", "jugo en polvo", "cerveza lata",
    "cerveza botella", "vino tinto", "vino blanco", "fernet", "aperitivo",
    "soda", "tónica", "energizante", "bebida isotónica",
    # Limpieza
    "detergente líquido", "jabón en polvo", "jabón líquido ropa",
    "suavizante", "lavandina", "desinfectante", "limpiavidrios",
    "limpiador pisos", "limpiador cocina", "limpiador baño",
    "esponja", "trapo piso", "bolsa residuos", "papel cocina",
    "papel higiénico", "servilletas", "insecticida", "desodorante ambiente",
    "cera pisos", "quitamanchas", "cloro", "soda cáustica",
    # Higiene personal
    "shampoo", "acondicionador", "jabón tocador", "desodorante",
    "pasta dental", "cepillo dental", "hilo dental", "enjuague bucal",
    "crema corporal", "protector solar", "toallitas húmedas",
    "algodón", "alcohol", "agua oxigenada", "curitas",
    "pañales", "toallitas femeninas", "tampones", "afeitadora",
    "espuma afeitar", "crema depilatoria",
    # Congelados
    "milanesa soja", "empanadas congeladas", "pizza congelada",
    "papas fritas congeladas", "helado", "vegetales congelados",
    "nuggets", "medallón", "suprema",
    # Panadería y repostería
    "facturas", "medialunas", "bizcochos", "tostadas", "grisines",
    "prepizza", "tortillas", "pan hamburguesa", "pan pancho",
    # Mascotas
    "alimento perro", "alimento gato", "piedras sanitarias",
    # Bazar y hogar
    "broches ropa", "pinzas ropa", "fósforos", "velas",
    "papel aluminio", "film", "bolsa freezer", "contenedor plástico",
    "pilas", "lamparita", "foco led",
    # Varios
    "pañuelos descartables", "barbijo", "guantes descartables",
    "carbón", "leña", "encendedor",
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
    zonas = [
        ("Zona Sur GBA", -34.83, -58.39, 12.0),
        ("CABA Centro", -34.61, -58.38, 15.0),
        ("GBA Norte", -34.47, -58.53, 12.0),
        ("GBA Oeste", -34.67, -58.63, 12.0),
        ("Cordoba Capital", -31.42, -64.18, 15.0),
        ("Rosario", -32.95, -60.65, 12.0),
        ("Mendoza Capital", -32.89, -68.83, 12.0),
        ("Tucuman Capital", -26.82, -65.20, 12.0),
        ("San Juan Capital", -31.54, -68.52, 12.0),
        ("Mar del Plata", -38.00, -57.55, 12.0),
    ]
    for nombre, lat, lng, radio in zonas:
        cur.execute("INSERT INTO zonas (nombre,lat,lng,radio_km) VALUES (%s,%s,%s,%s) ON CONFLICT (nombre) DO NOTHING", (nombre, lat, lng, radio))
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
import logging
log = logging.getLogger(__name__)


def _run_catalog_scraper():
    if not _DB_URL: return
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
                    # Skip products with no stock (discontinued, stale prices)
                    avail = int(offer.get("AvailableQuantity", 0))
                    if avail <= 0: continue
                    images = item.get("images", [])
                    image = images[0]["imageUrl"] if images else ""
                except: pass
                if price <= 0: continue
                # Filter out invalid prices (very low prices are likely per gram/ml)
                if price < 100: continue
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
                    INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
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

    # Also scrape chains via SEPA (those without VTEX)
    for sepa_chain, sepa_kw in [('Coto',['coto']),('Vea',['vea']),('Makro',['makro']),('Diarco',['diarco']),('Maxiconsumo',['maxiconsumo'])]:
        try:
            _scrape_chain_sepa(sepa_chain, sepa_kw, total)
        except Exception as e:
            log.warning(f'Error scraping {sepa_chain} via SEPA: {e}')
    _scrape_changomas_sepa(total)

    log.info(f"=== VTEX scraper done: {total} products in {_time.time()-start:.0f}s ===")


def _scrape_chain_sepa(chain_name, keywords, vtex_total):
    """Scrape a chain via SEPA API."""
    log.info(f"Scraping {chain_name} via SEPA...")
    SEPA = 'https://d3e6htiiul5ek9.cloudfront.net/prod'
    _headers = {'User-Agent': 'Mozilla/5.0 UhNoHabia/1.0'}
    count = 0
    search_terms = ['leche','arroz','fideos','aceite','harina','azucar','yerba','cafe','huevos','pollo','carne','cerveza','vino','gaseosa','agua','detergente','jabon','shampoo','papel']
    for term in search_terms:
        try:
            r = _requests.get(f'{SEPA}/productos', params={'string':term,'lat':'-34.6','lng':'-58.4'}, headers=_headers, timeout=10)
            if r.status_code != 200: continue
            for p in r.json().get('productos',[])[:20]:
                pid = p.get('id','')
                if not pid: continue
                pr = _requests.get(f'{SEPA}/producto', params={'id_producto':pid,'lat':'-34.6','lng':'-58.4'}, headers=_headers, timeout=10)
                if pr.status_code != 200: continue
                for suc in pr.json().get('sucursales',[]):
                    cadena = suc.get('banderaDescripcion','')
                    if not any(kw in cadena.lower() for kw in keywords): continue
                    precio = suc.get('preciosProducto',{}).get('precioLista')
                    if not precio: continue
                    try: precio = float(precio)
                    except: continue
                    if precio < 100: continue
                    doc_id = f'{chain_name}_{pid}'
                    cn = _pg(); cr = cn.cursor()
                    from psycopg2.extras import execute_values
                    execute_values(cr, '''INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
                        VALUES %s ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio,updated_at=NOW()''',
                        [(doc_id, p.get('nombre',''), p.get('marca',''), p.get('presentacion',''),
                          p.get('nombre','').lower(), p.get('marca','').lower(), chain_name, precio, precio, '')])
                    cn.commit(); cr.close(); cn.close()
                    count += 1
                _time.sleep(0.3)
        except Exception as e:
            log.warning(f'{chain_name} SEPA error for {term}: {e}')
    log.info(f'{chain_name} (SEPA): {count} products')

def _scrape_changomas_sepa(vtex_total):
    """Scrape Changomas prices via SEPA API (they don't have VTEX)."""
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
                    if "changomas" not in cadena.lower() and "walmart" not in cadena.lower() and "coto" not in cadena.lower():
                        continue
                    pp = suc.get("preciosProducto", {})
                    precio = pp.get("precioLista")
                    if not precio or float(precio) < 100: continue
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
    results = [{"id":r[0],"nombre":r[1],"marca":r[2],"presentacion":r[3],"cadena":r[4],"precio":r[5],"precioLista":r[6],"imagen":r[7]} for r in rows if r[5] >= _min_price_for(r[4])]

    # If few results from DB, try real-time VTEX search as fallback
    if len(results) < 5 and not cadena:
        _VTEX_FALLBACK = {
            "DIA": "https://diaonline.supermercadosdia.com.ar",
            "Jumbo": "https://www.jumbo.com.ar",
            "Disco": "https://www.disco.com.ar",
            "Carrefour": "https://www.carrefour.com.ar",
            "Changomas": "https://www.masonline.com.ar",
        }
        existing_ids = {r["id"] for r in results}
        for cad, base_url in _VTEX_FALLBACK.items():
            try:
                r = _requests.get(f"{base_url}/api/catalog_system/pub/products/search/{q}",
                    params={"_from": 0, "_to": 9},
                    headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"}, timeout=8)
                if r.status_code not in (200, 206): continue
                data = r.json()
                if not isinstance(data, list): continue
                for p in data[:10]:
                    try:
                        pid = p.get("productId", "")
                        doc_id = f"{cad}_{pid}"
                        if doc_id in existing_ids: continue
                        name = p.get("productName", "")
                        # Check all search words are in the product name
                        name_lower = name.lower()
                        if not all(w in name_lower for w in words): continue
                        price = float(p["items"][0]["sellers"][0]["commertialOffer"]["Price"])
                        if price < _min_price_for(cad): continue
                        if int(p["items"][0]["sellers"][0]["commertialOffer"].get("AvailableQuantity", 0)) <= 0: continue
                        brand = p.get("brand", "")
                        if marca and marca.lower() not in brand.lower(): continue
                        images = p.get("items", [{}])[0].get("images", [])
                        image = images[0]["imageUrl"] if images else ""
                        results.append({"id": doc_id, "nombre": name, "marca": brand, "presentacion": "",
                            "cadena": cad, "precio": price, "precioLista": price, "imagen": image})
                        existing_ids.add(doc_id)
                    except: continue
            except: continue
        # Also try SEPA for Coto and other chains not in VTEX
        try:
            r = _requests.get(f"{SEPA_BASE_URL}/productos",
                params={"string": q, "lat": "-34.83", "lng": "-58.39"},
                headers={"User-Agent": "Mozilla/5.0"}, timeout=10)
            if r.status_code == 200:
                sepa_data = r.json()
                for sp in sepa_data.get("productos", [])[:10]:
                    sp_name = sp.get("nombre", "")
                    sp_lower = sp_name.lower()
                    if not all(w in sp_lower for w in words): continue
                    sp_marca = sp.get("marca", "")
                    if marca and marca.lower() not in sp_marca.lower(): continue
                    sp_id = sp.get("id", "")
                    # Get prices from SEPA
                    try:
                        pr = _requests.get(f"{SEPA_BASE_URL}/producto",
                            params={"id_producto": sp_id, "lat": "-34.83", "lng": "-58.39"},
                            headers={"User-Agent": "Mozilla/5.0"}, timeout=10)
                        if pr.status_code == 200:
                            pdata = pr.json()
                            for suc in pdata.get("sucursales", [])[:5]:
                                cad_name = suc.get("banderaDescripcion", "")
                                pp = suc.get("preciosProducto", {})
                                precio = pp.get("precioLista")
                                if not precio or float(precio) < 100: continue
                                doc_id = f"SEPA_{sp_id}_{cad_name[:10]}"
                                if doc_id in existing_ids: continue
                                results.append({"id": doc_id, "nombre": sp_name, "marca": sp_marca,
                                    "presentacion": sp.get("presentacion", ""), "cadena": cad_name,
                                    "precio": float(precio), "precioLista": float(precio), "imagen": ""})
                                existing_ids.add(doc_id)
                    except: pass
        except: pass

    return results[:limit]

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
        if precio < _min_price_for(cadena): continue
        key = f"{nombre}|{marca}"
        if key not in groups:
            groups[key] = {"nombre":nombre,"marca":marca,"presentacion":pres,"preciosPorCadena":[]}
        groups[key]["preciosPorCadena"].append({"cadena":cadena,"productoId":pid,"precio":precio})
    result = list(groups.values())[:50]
    
    # If few results, try real-time VTEX search to fill gaps
    if len(result) < 5:
        _VTEX_URLS = {
            "DIA": "https://diaonline.supermercadosdia.com.ar",
            "Jumbo": "https://www.jumbo.com.ar",
            "Disco": "https://www.disco.com.ar",
            "Carrefour": "https://www.carrefour.com.ar",
            "Changomas": "https://www.masonline.com.ar",
        }
        existing_names = {g["nombre"].lower() for g in result}
        for cadena_name, base_url in _VTEX_URLS.items():
            try:
                r = _requests.get(f"{base_url}/api/catalog_system/pub/products/search/{q}",
                    params={"_from": 0, "_to": 9},
                    headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"}, timeout=8)
                if r.status_code not in (200, 206): continue
                data = r.json()
                if not isinstance(data, list): continue
                for p in data[:5]:
                    try:
                        name = p.get("productName", "")
                        if name.lower() in existing_names: continue
                        price = float(p["items"][0]["sellers"][0]["commertialOffer"]["Price"])
                        if price < _min_price_for(cadena_name): continue
                        if int(p["items"][0]["sellers"][0]["commertialOffer"].get("AvailableQuantity", 0)) <= 0: continue
                        brand = p.get("brand", "")
                        key = f"{name}|{brand}"
                        if key not in groups:
                            groups[key] = {"nombre": name, "marca": brand, "presentacion": "", "preciosPorCadena": []}
                        groups[key]["preciosPorCadena"].append({"cadena": cadena_name, "productoId": f"{cadena_name}_{p.get('productId','')}", "precio": price})
                        existing_names.add(name.lower())
                    except: continue
            except: continue
        result = list(groups.values())[:50]
    
    return result

@app.post("/catalog/optimizar")
def catalog_optimizar(body: dict):
    """Calculate optimal supermarket + payment split for a shopping list."""
    if not _DB_URL: return {"error":"DB not configured"}
    productos_req = body.get("productos", [])
    medios_ids = body.get("medios_pago_ids", [])
    tarjetas_sel = body.get("tarjetas_seleccionadas", {})
    dia = body.get("dia_semana", "")
    cadenas_filter = body.get("cadenas", [])

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

    # Map cadena -> VTEX base URL for real-time fallback search
    _CADENA_VTEX = {
        "DIA": "https://diaonline.supermercadosdia.com.ar",
        "Jumbo": "https://www.jumbo.com.ar",
        "Disco": "https://www.disco.com.ar",
        "Carrefour": "https://www.carrefour.com.ar",
        "Changomas": "https://www.masonline.com.ar",
    }

    def _search_vtex_realtime(cadena_name, query):
        """Fallback: search VTEX API in real-time when product not in DB."""
        base = _CADENA_VTEX.get(cadena_name)
        if not base:
            return None
        try:
            r = _requests.get(f"{base}/api/catalog_system/pub/products/search/{query}",
                params={"_from": 0, "_to": 9},
                headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"}, timeout=10)
            if r.status_code not in (200, 206):
                return None
            data = r.json()
            if not isinstance(data, list) or not data:
                return None
            best = None
            for p in data:
                try:
                    price = float(p["items"][0]["sellers"][0]["commertialOffer"]["Price"])
                    if price < _min_price_for(cadena_name):
                        continue
                    if int(p["items"][0]["sellers"][0]["commertialOffer"].get("AvailableQuantity", 0)) <= 0:
                        continue
                    name = p.get("productName", "")
                    brand = p.get("brand", "")
                    if best is None or price < best[2]:
                        best = (name, brand, price)
                except:
                    continue
            return best
        except:
            return None

    def _extract_weight_grams(product_name):
        """Extract weight in grams from product name. Returns (grams, unit_str) or (None, None)."""
        import re
        name = product_name.lower()
        # Match patterns like "1 kg", "500 g", "1kg", "500g", "1.5 kg", "250 ml", "1 l", "1 lt"
        m = re.search(r'(\d+(?:[.,]\d+)?)\s*(kg|kgm|g|gr|grs|ml|cc|l|lt|lts|litro|litros)\b', name)
        if not m:
            return None, None
        val = float(m.group(1).replace(',', '.'))
        unit = m.group(2).lower()
        if unit in ('kg', 'kgm'):
            return val * 1000, 'g'
        elif unit in ('g', 'gr', 'grs'):
            return val, 'g'
        elif unit in ('l', 'lt', 'lts', 'litro', 'litros'):
            return val * 1000, 'ml'
        elif unit in ('ml', 'cc'):
            return val, 'ml'
        return None, None

    def _calc_units_needed(product_name, requested_qty, requested_unit):
        """Calculate how many units of a product to buy based on requested quantity and unit.
        Returns the number of units needed."""
        if not requested_unit:
            return requested_qty
        req_unit = requested_unit.lower().strip()
        prod_weight, prod_unit_type = _extract_weight_grams(product_name)
        if prod_weight is None:
            return requested_qty
        # Convert requested quantity to grams/ml
        if req_unit in ('kg', 'kilo', 'kilos'):
            req_grams = requested_qty * 1000
        elif req_unit in ('g', 'gr', 'grs', 'gramos'):
            req_grams = requested_qty
        elif req_unit in ('l', 'lt', 'lts', 'litro', 'litros'):
            req_grams = requested_qty * 1000
        elif req_unit in ('ml', 'cc'):
            req_grams = requested_qty
        else:
            return requested_qty  # Unidad, etc - just use quantity as-is
        import math
        return max(1, math.ceil(req_grams / prod_weight))

    ranking = []
    for cadena in cadenas:
        total = 0.0
        missing = []
        selected_products = []
        for prod in productos_req:
            pid = prod.get("producto_id")
            nombre = prod.get("nombre", "")
            qty = prod.get("cantidad", 1)
            unidad = prod.get("unidad", "")
            if pid and pid != "cualquier_marca":
                cr.execute("SELECT nombre,marca,precio FROM vtex_productos WHERE id=%s", (pid,))
                row = cr.fetchone()
                if row:
                    units = _calc_units_needed(row[0], qty, unidad) if unidad else qty
                    total += row[2] * units
                    selected_products.append({"nombre": row[0], "marca": row[1], "precio": row[2], "cantidad": units, "busqueda": nombre})
                else:
                    missing.append(pid)
            else:
                # cualquier_marca: find cheapest in this chain considering weight
                nombre_lower = nombre.lower().strip()
                words = nombre_lower.split()
                found = False
                if words:
                    conditions = " AND ".join(["nombre_lower LIKE %s"] * len(words))
                    params = [f"%{w}%" for w in words]
                    # Get top 10 candidates to evaluate cost per unit
                    cr.execute(f"""SELECT nombre,marca,precio FROM vtex_productos 
                        WHERE cadena=%s AND {conditions} 
                        ORDER BY CASE WHEN nombre_lower LIKE %s THEN 0 WHEN nombre_lower LIKE %s THEN 1 ELSE 2 END, precio 
                        LIMIT 10""",
                        [cadena] + params + [f"{nombre_lower}%", f"{words[0]}%"])
                    rows = cr.fetchall()
                    if rows:
                        # If unit is weight-based, calculate actual cost for each candidate
                        if unidad and unidad.lower().strip() in ('kg', 'kilo', 'kilos', 'g', 'gr', 'l', 'lt', 'litro', 'litros', 'ml', 'cc'):
                            best_cost = None
                            best_row = None
                            best_units = qty
                            for row in rows:
                                units = _calc_units_needed(row[0], qty, unidad)
                                cost = row[2] * units
                                if best_cost is None or cost < best_cost:
                                    best_cost = cost
                                    best_row = row
                                    best_units = units
                            if best_row:
                                total += best_cost
                                selected_products.append({"nombre": best_row[0], "marca": best_row[1], "precio": best_row[2], "cantidad": best_units, "busqueda": nombre})
                                found = True
                        else:
                            row = rows[0]
                            total += row[2] * qty
                            selected_products.append({"nombre": row[0], "marca": row[1], "precio": row[2], "cantidad": qty, "busqueda": nombre})
                            found = True
                # Strategy 2: Real-time VTEX API search (fallback)
                if not found:
                    rt = _search_vtex_realtime(cadena, nombre)
                    if rt:
                        units = _calc_units_needed(rt[0], qty, unidad) if unidad else qty
                        total += rt[2] * units
                        selected_products.append({"nombre": rt[0], "marca": rt[1], "precio": rt[2], "cantidad": units, "busqueda": nombre})
                        found = True
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
    if cadenas_filter:
        ranking = [r for r in ranking if r["cadena"] in cadenas_filter]
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
        "rankingCadenas": [{"cadena":r["cadena"],"totalOriginal":r["totalOriginal"],"totalFinal":r["totalFinal"],"ahorro":r["ahorro"],
            "ahorroPorcentaje":r["ahorroPorcentaje"],"distribucionPagos":r["distribucionPagos"],
            "productosFaltantes":r["productosFaltantes"],"productosSeleccionados":r["productosSeleccionados"]} for r in ranking]
    }

# Remove old endpoints that are no longer needed
# /catalog/precios, /catalog/localidades, /catalog/zonas are removed
# Products now include price directly from VTEX

@app.on_event("startup")
def _on_startup():
    if _DB_URL:
        _init_catalog_db()
        _init_token_store()
        threading.Thread(target=_run_catalog_scraper, daemon=True).start()
        from apscheduler.schedulers.background import BackgroundScheduler
        sched = BackgroundScheduler()
        sched.add_job(_run_catalog_scraper, "cron", hour=6, minute=0)
        sched.start()