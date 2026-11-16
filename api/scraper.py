"""Inicializacion de la DB del catalogo y scraper interno (VTEX + SEPA)."""
import logging
import threading
import time

from psycopg2.extras import execute_values

from api import db
from api.catalog_data import (BANCOS, BILLETERAS, CLUBES, PROMOS_SEMILLA, SCHEMA_SQL,
                              SCRAPE_TERMS, ZONAS)
from api.sources import VTEX_SCRAPE_STORES, sepa_get, vtex_offer, vtex_search

log = logging.getLogger(__name__)

_INSERT_PRODUCT = """INSERT INTO vtex_productos
    (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen) VALUES %s
    ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio, updated_at=NOW()"""

_UPSERT_PRODUCT_FULL = """INSERT INTO vtex_productos
    (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen) VALUES %s
    ON CONFLICT (id) DO UPDATE SET
    nombre=EXCLUDED.nombre, marca=EXCLUDED.marca, precio=EXCLUDED.precio,
    precio_lista=EXCLUDED.precio_lista, imagen=EXCLUDED.imagen,
    nombre_lower=EXCLUDED.nombre_lower, marca_lower=EXCLUDED.marca_lower, updated_at=NOW()"""

# Evita que dos corridas del scraper se pisen (cron + startup + /catalog/scrape).
_run_lock = threading.Lock()


def init_catalog_db():
    with db.cursor(commit=True) as cur:
        for sql in SCHEMA_SQL:
            cur.execute(sql)
        for nombre, lat, lng, radio in ZONAS:
            cur.execute("INSERT INTO zonas (nombre,lat,lng,radio_km) VALUES (%s,%s,%s,%s) "
                        "ON CONFLICT (nombre) DO NOTHING", (nombre, lat, lng, radio))
    _seed_promos()
    _seed_medios_pago()


def _seed_promos():
    with db.cursor(commit=True) as cur:
        cur.execute("SELECT COUNT(*) FROM promos_bancarias")
        if cur.fetchone()[0] > 0:
            return
        for p in PROMOS_SEMILLA:
            cur.execute("INSERT INTO promos_bancarias (cadena,banco,tarjeta,descuento_pct,dia_semana,"
                        "tope_reintegro,vigencia_desde,vigencia_hasta,condiciones) "
                        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)", p)


def _seed_medios_pago():
    with db.cursor(commit=True) as cur:
        cur.execute("SELECT COUNT(*) FROM medios_pago")
        if cur.fetchone()[0] > 0:
            return
        for nombre, tarjetas in BANCOS:
            cur.execute("INSERT INTO medios_pago (nombre,tipo,nombre_display,tarjetas_disponibles) "
                        "VALUES (%s,'banco',%s,%s)", (nombre, f"Banco {nombre}", tarjetas))
        for nombre in BILLETERAS:
            cur.execute("INSERT INTO medios_pago (nombre,tipo,nombre_display) VALUES (%s,'billetera_digital',%s)",
                        (nombre, nombre))
        for nombre in CLUBES:
            cur.execute("INSERT INTO medios_pago (nombre,tipo,nombre_display) VALUES (%s,'club_beneficios',%s)",
                        (nombre, nombre))
        # Vincular promos existentes con su medio de pago
        cur.execute("SELECT id,nombre FROM medios_pago")
        mp_map = {r[1].lower(): r[0] for r in cur.fetchall()}
        cur.execute("SELECT id,banco FROM promos_bancarias WHERE medio_pago_id IS NULL")
        for pid, banco in cur.fetchall():
            mp_id = mp_map.get(banco.lower()) or mp_map.get(f"banco {banco}".lower())
            if mp_id:
                cur.execute("UPDATE promos_bancarias SET medio_pago_id=%s WHERE id=%s", (mp_id, pid))


def hours_since_last_run() -> float | None:
    with db.cursor() as cur:
        cur.execute("SELECT EXTRACT(EPOCH FROM (NOW() - MAX(ts))) / 3600 FROM scraper_runs")
        row = cur.fetchone()
    return float(row[0]) if row and row[0] is not None else None


def is_running() -> bool:
    return _run_lock.locked()


def run_catalog_scraper():
    """Corre el scraper completo. Si ya hay una corrida en curso, no hace nada."""
    if not db.enabled():
        return
    if not _run_lock.acquire(blocking=False):
        log.info("Scraper ya en ejecucion; se ignora la nueva solicitud")
        return
    try:
        _scrape_all()
    except Exception:
        log.exception("El scraper fallo")
    finally:
        _run_lock.release()


def _scrape_all():
    log.info("=== VTEX Catalog scraper start ===")
    start = time.time()
    total = 0
    for cadena, base_url in VTEX_SCRAPE_STORES.items():
        store_count = 0
        for term in SCRAPE_TERMS:
            rows = []
            for p in vtex_search(base_url, term):
                pid = p.get("productId", "")
                offer = vtex_offer(p)
                if not pid or not offer:
                    continue
                price, stock, image = offer
                # Sin stock = discontinuado/precio viejo; < 100 suele ser precio por gramo/ml
                if stock <= 0 or price < 100:
                    continue
                list_price = price
                try:
                    list_price = float(p["items"][0]["sellers"][0]["commertialOffer"].get("ListPrice", price))
                    item = p["items"][0]
                    presentacion = f'{item.get("measurementUnit", "")} {item.get("unitMultiplier", "")}'.strip()
                except (KeyError, IndexError, TypeError, ValueError):
                    presentacion = ""
                name, brand = p.get("productName", ""), p.get("brand", "")
                rows.append((f"{cadena}_{pid}", name, brand, presentacion, name.lower(), brand.lower(),
                             cadena, price, list_price, image))
            if rows:
                with db.cursor(commit=True) as cur:
                    execute_values(cur, _UPSERT_PRODUCT_FULL, rows)
                store_count += len(rows)
            time.sleep(0.5)  # rate limit
        total += store_count
        log.info("  %s: %d products", cadena, store_count)

    with db.cursor(commit=True) as cur:
        cur.execute("INSERT INTO scraper_runs (duration,products,prices) VALUES (%s,%s,%s)",
                    (time.time() - start, total, total))

    # Cadenas sin VTEX, via SEPA
    for chain, kws in [("Coto", ["coto"]), ("Vea", ["vea"]), ("Makro", ["makro"]),
                       ("Diarco", ["diarco"]), ("Maxiconsumo", ["maxiconsumo"])]:
        try:
            _scrape_chain_sepa(chain, kws)
        except Exception as e:
            log.warning("Error scraping %s via SEPA: %s", chain, e)
    _scrape_changomas_sepa()
    log.info("=== scraper done: %d products in %.0fs ===", total, time.time() - start)


_SEPA_CHAIN_TERMS = ["leche", "arroz", "fideos", "aceite", "harina", "azucar", "yerba", "cafe", "huevos",
                     "pollo", "carne", "cerveza", "vino", "gaseosa", "agua", "detergente", "jabon",
                     "shampoo", "papel"]


def _scrape_chain_sepa(chain_name: str, keywords: list[str]):
    count = 0
    for term in _SEPA_CHAIN_TERMS:
        data = sepa_get("/productos", {"string": term, "lat": "-34.6", "lng": "-58.4"})
        if not data:
            continue
        rows = []
        for p in data.get("productos", [])[:20]:
            pid = p.get("id", "")
            if not pid:
                continue
            pdata = sepa_get("/producto", {"id_producto": pid, "lat": "-34.6", "lng": "-58.4"})
            for suc in (pdata or {}).get("sucursales", []):
                if not any(kw in suc.get("banderaDescripcion", "").lower() for kw in keywords):
                    continue
                try:
                    precio = float(suc.get("preciosProducto", {}).get("precioLista") or 0)
                except (TypeError, ValueError):
                    continue
                if precio < 100:
                    continue
                rows.append((f"{chain_name}_{pid}", p.get("nombre", ""), p.get("marca", ""),
                             p.get("presentacion", ""), p.get("nombre", "").lower(),
                             p.get("marca", "").lower(), chain_name, precio, precio, ""))
            time.sleep(0.3)
        rows = list({r[0]: r for r in rows}.values())  # un precio por producto
        if rows:
            with db.cursor(commit=True) as cur:
                execute_values(cur, _INSERT_PRODUCT, rows)
            count += len(rows)
    log.info("%s (SEPA): %d products", chain_name, count)


def _scrape_changomas_sepa():
    count = 0
    for term in SCRAPE_TERMS:
        data = sepa_get("/productos", {"string": term, "lat": "-34.83", "lng": "-58.39"}, timeout=15)
        for p in (data or {}).get("productos", [])[:25]:
            pid = p.get("id", "")
            if not pid:
                continue
            pdata = sepa_get("/producto", {"id_producto": pid, "lat": "-34.83", "lng": "-58.39"}, timeout=15)
            rows = []
            for suc in (pdata or {}).get("sucursales", []):
                cadena = suc.get("banderaDescripcion", "").lower()
                if "changomas" not in cadena and "walmart" not in cadena and "coto" not in cadena:
                    continue
                try:
                    precio = float(suc.get("preciosProducto", {}).get("precioLista") or 0)
                except (TypeError, ValueError):
                    continue
                if precio < 100 or suc.get("distanciaNumero", 999) > 15:
                    continue
                rows.append((f"Changomas_{pid}", p.get("nombre", ""), p.get("marca", ""),
                             p.get("presentacion", ""), p.get("nombre", "").lower(),
                             p.get("marca", "").lower(), "Changomas", precio, precio, ""))
            rows = list({r[0]: r for r in rows}.values())
            if rows:
                with db.cursor(commit=True) as cur:
                    execute_values(cur, _INSERT_PRODUCT, rows)
                count += len(rows)
            time.sleep(0.3)
        time.sleep(0.3)
    log.info("Changomas (SEPA): %d products", count)
