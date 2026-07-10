"""
Scraper de Precios Claros (SEPA) + API REST.
Backend: Postgres (Neon free tier) + FastAPI.
Deploy: Koyeb.

Zona inicial: Sur GBA (Glew, Longchamps, Burzaco, Adrogué,
Temperley, Lomas de Zamora, Llavallol, Turdera).
"""
import os
import time
import logging
import threading
import math
import requests
from datetime import datetime, timezone
from contextlib import asynccontextmanager

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware

import db as database

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

SEPA_BASE = "https://d3e6htiiul5ek9.cloudfront.net/prod"
HEADERS = {"User-Agent": "UhNoHabia-Scraper/1.0"}

SEARCH_TERMS = [
    "leche", "arroz", "fideos", "aceite", "harina", "azucar", "yerba",
    "cafe", "te", "galletitas", "pan", "manteca", "queso", "yogur",
    "huevos", "pollo", "carne", "jamon", "salchichas", "atun",
    "tomate", "mayonesa", "mostaza", "ketchup", "sal",
    "cerveza", "vino", "gaseosa", "agua", "jugo",
    "detergente", "jabon", "shampoo", "papel higienico", "lavandina",
    "desodorante", "pasta dental", "pañales",
    "pure de tomate", "mermelada", "dulce de leche",
    "cereales", "avena", "polenta", "lentejas",
]


def haversine_km(lat1, lng1, lat2, lng2):
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))


def fetch_json(url, params=None, retries=3):
    for attempt in range(retries):
        try:
            resp = requests.get(url, params=params, headers=HEADERS, timeout=15)
            if resp.status_code == 200:
                return resp.json()
        except Exception as e:
            logger.warning(f"Attempt {attempt+1} failed: {e}")
        time.sleep(1 * (attempt + 1))
    return None


def run_scraper():
    """Main scraper: fetch sucursales in zone, then products+prices."""
    logger.info("=== Starting scraper ===")
    start = time.time()

    conn = database.get_conn()
    cur = conn.cursor()
    cur.execute("SELECT id, nombre, lat, lng, radio_km FROM zonas;")
    zonas = cur.fetchall()
    cur.close()
    conn.close()

    total_products = 0
    total_prices = 0

    for zona_id, zona_nombre, zona_lat, zona_lng, radio_km in zonas:
        logger.info(f"Processing zone: {zona_nombre} ({zona_lat}, {zona_lng}, r={radio_km}km)")

        # 1. Fetch sucursales (paginated, SEPA returns 30 per page)
        suc_rows = []
        suc_ids = []
        offset = 0
        while True:
            data = fetch_json(f"{SEPA_BASE}/sucursales", {
                "lat": str(zona_lat), "lng": str(zona_lng),
                "offset": str(offset), "limit": "30"
            })
            if not data or "sucursales" not in data or not data["sucursales"]:
                break

            for s in data["sucursales"]:
                sid = s.get("id", "")
                slat = s.get("lat", "")
                slng = s.get("lng", "")
                if not sid or not slat or not slng:
                    continue
                try:
                    dist = haversine_km(zona_lat, zona_lng, float(slat), float(slng))
                except (ValueError, TypeError):
                    continue
                if dist > radio_km:
                    continue

                cadena = s.get("banderaDescripcion", s.get("comercioRazonSocial", ""))
                suc_rows.append((
                    sid, cadena, s.get("sucursalNombre", ""), s.get("direccion", ""),
                    s.get("localidad", ""), s.get("provincia", ""),
                    slat, slng, zona_id
                ))
                suc_ids.append(sid)

            total = data.get("total", 0)
            offset += 30
            if offset >= total:
                break
            time.sleep(0.3)

        database.upsert_sucursales(suc_rows)
        logger.info(f"Zone {zona_nombre}: {len(suc_ids)} sucursales within {radio_km}km")

        if not suc_ids:
            continue

        # 2. Fetch products and prices
        for term in SEARCH_TERMS:
            pdata = fetch_json(f"{SEPA_BASE}/productos", {
                "string": term, "lat": str(zona_lat), "lng": str(zona_lng)
            })
            if not pdata or "productos" not in pdata:
                continue

            productos = pdata["productos"][:25]
            prod_rows = []
            for p in productos:
                pid = p.get("id", "")
                if not pid:
                    continue
                nombre = p.get("nombre", "")
                marca = p.get("marca", "")
                presentacion = p.get("presentacion", "")
                prod_rows.append((pid, nombre, marca, presentacion, nombre.lower(), marca.lower()))

            database.upsert_productos(prod_rows)
            total_products += len(prod_rows)

            # Prices for each product using lat/lng (returns sucursales with prices)
            for p in productos:
                pid = p.get("id", "")
                if not pid:
                    continue
                prdata = fetch_json(f"{SEPA_BASE}/producto", {
                    "id_producto": pid, "lat": str(zona_lat), "lng": str(zona_lng)
                })
                if not prdata or "sucursales" not in prdata:
                    continue

                price_rows = []
                for suc in prdata["sucursales"]:
                    suc_id_raw = suc.get("id", "")
                    comercio_id = suc.get("comercioId", "")
                    bandera_id = suc.get("banderaId", "")
                    # Reconstruct full sucursal ID: comercioId-banderaId-id
                    suc_id = f"{comercio_id}-{bandera_id}-{suc_id_raw}"

                    precios_prod = suc.get("preciosProducto", {})
                    precio_val = precios_prod.get("precioLista")
                    if not precio_val or precio_val == "":
                        continue

                    dist = suc.get("distanciaNumero", 999)
                    if dist > radio_km:
                        continue

                    cadena = suc.get("banderaDescripcion", "")
                    localidad = suc.get("localidad", "")
                    direccion = suc.get("direccion", "")
                    suc_nombre = suc.get("sucursalNombre", "")

                    # Ensure sucursal exists in DB
                    database.upsert_sucursales([(
                        suc_id, cadena, suc_nombre, direccion,
                        localidad, suc.get("provincia", ""),
                        suc.get("lat", ""), suc.get("lng", ""), zona_id
                    )])

                    doc_id = f"{pid}_{suc_id}"
                    price_rows.append((
                        doc_id, pid, suc_id, p.get("nombre", ""),
                        cadena, float(precio_val), ""
                    ))

                if price_rows:
                    database.upsert_precios(price_rows)
                    total_prices += len(price_rows)

                time.sleep(0.3)
            time.sleep(0.3)

        elapsed = time.time() - start
        database.save_scraper_status(zona_id, elapsed, total_products, total_prices)

    logger.info(f"=== Scraper done: {total_products} products, {total_prices} prices in {time.time()-start:.1f}s ===")


# === FastAPI REST API ===

@asynccontextmanager
async def lifespan(app: FastAPI):
    database.init_db()
    database.seed_zonas()
    # Run scraper on startup in background
    threading.Thread(target=run_scraper, daemon=True).start()
    # Schedule daily at 6AM
    from apscheduler.schedulers.background import BackgroundScheduler
    scheduler = BackgroundScheduler()
    scheduler.add_job(run_scraper, "cron", hour=6, minute=0)
    scheduler.start()
    yield
    scheduler.shutdown()

app = FastAPI(title="Precios Claros API - Zona Sur GBA", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/api/zonas")
def get_zonas():
    conn = database.get_conn()
    cur = conn.cursor()
    cur.execute("SELECT id, nombre, lat, lng, radio_km FROM zonas ORDER BY nombre;")
    rows = cur.fetchall()
    cur.close(); conn.close()
    return [{"id": r[0], "nombre": r[1], "lat": r[2], "lng": r[3], "radioKm": r[4]} for r in rows]


@app.get("/api/productos")
def search_productos(
    q: str = Query(..., min_length=2, description="Buscar por nombre"),
    marca: str = Query(None, description="Filtrar por marca"),
    limit: int = Query(30, le=100)
):
    conn = database.get_conn()
    cur = conn.cursor()
    query_lower = f"%{q.lower().strip()}%"
    if marca:
        cur.execute("""
        SELECT id, nombre, marca, presentacion FROM productos
        WHERE nombre_lower LIKE %s AND marca_lower LIKE %s
        ORDER BY nombre LIMIT %s;
        """, (query_lower, f"%{marca.lower().strip()}%", limit))
    else:
        cur.execute("""
        SELECT id, nombre, marca, presentacion FROM productos
        WHERE nombre_lower LIKE %s ORDER BY nombre LIMIT %s;
        """, (query_lower, limit))
    rows = cur.fetchall()
    cur.close(); conn.close()
    return [{"id": r[0], "nombre": r[1], "marca": r[2], "presentacion": r[3]} for r in rows]


@app.get("/api/precios/{producto_id}")
def get_precios(
    producto_id: str,
    cadena: str = Query(None, description="Filtrar por cadena"),
    zona_id: int = Query(None, description="Filtrar por zona"),
    localidad: str = Query(None, description="Filtrar por localidad")
):
    conn = database.get_conn()
    cur = conn.cursor()
    sql = """
    SELECT p.id, p.producto_id, p.sucursal_id, p.producto_nombre, p.cadena, p.precio, p.fecha,
           s.nombre as suc_nombre, s.direccion, s.localidad
    FROM precios p
    JOIN sucursales s ON p.sucursal_id = s.id
    WHERE p.producto_id = %s
    """
    params = [producto_id]
    if cadena:
        sql += " AND LOWER(p.cadena) LIKE %s"
        params.append(f"%{cadena.lower()}%")
    if zona_id:
        sql += " AND s.zona_id = %s"
        params.append(zona_id)
    if localidad:
        sql += " AND LOWER(s.localidad) LIKE %s"
        params.append(f"%{localidad.lower()}%")
    sql += " ORDER BY p.precio ASC LIMIT 100;"
    cur.execute(sql, params)
    rows = cur.fetchall()
    cur.close(); conn.close()
    return [{
        "id": r[0], "productoId": r[1], "sucursalId": r[2], "productoNombre": r[3],
        "cadena": r[4], "precio": r[5], "fecha": r[6],
        "sucursalNombre": r[7], "direccion": r[8], "localidad": r[9]
    } for r in rows]


@app.get("/api/cadenas")
def get_cadenas(zona_id: int = Query(None)):
    conn = database.get_conn()
    cur = conn.cursor()
    if zona_id:
        cur.execute("SELECT DISTINCT cadena FROM sucursales WHERE zona_id = %s ORDER BY cadena;", (zona_id,))
    else:
        cur.execute("SELECT DISTINCT cadena FROM sucursales ORDER BY cadena;")
    rows = cur.fetchall()
    cur.close(); conn.close()
    return [r[0] for r in rows if r[0]]


@app.get("/api/localidades")
def get_localidades(zona_id: int = Query(None)):
    conn = database.get_conn()
    cur = conn.cursor()
    if zona_id:
        cur.execute("SELECT DISTINCT localidad FROM sucursales WHERE zona_id = %s ORDER BY localidad;", (zona_id,))
    else:
        cur.execute("SELECT DISTINCT localidad FROM sucursales ORDER BY localidad;")
    rows = cur.fetchall()
    cur.close(); conn.close()
    return [r[0] for r in rows if r[0]]


@app.get("/api/status")
def get_status():
    conn = database.get_conn()
    cur = conn.cursor()
    cur.execute("SELECT timestamp, duration_seconds, products_count, prices_count FROM scraper_status ORDER BY timestamp DESC LIMIT 1;")
    row = cur.fetchone()
    cur.execute("SELECT COUNT(*) FROM productos;")
    total_products = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM precios;")
    total_prices = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sucursales;")
    total_sucursales = cur.fetchone()[0]
    cur.close(); conn.close()
    if row:
        return {
            "lastRun": str(row[0]), "durationSeconds": row[1],
            "lastRunProducts": row[2], "lastRunPrices": row[3],
            "totalProducts": total_products, "totalPrices": total_prices,
            "totalSucursales": total_sucursales
        }
    return {"lastRun": None, "totalProducts": total_products, "totalPrices": total_prices, "totalSucursales": total_sucursales}


@app.post("/api/scrape")
def trigger_scrape():
    threading.Thread(target=run_scraper, daemon=True).start()
    return {"status": "started"}


if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port)
