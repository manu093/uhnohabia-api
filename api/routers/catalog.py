"""Catalogo de productos/precios (Postgres) y optimizador de compra."""
import logging
import threading
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
from typing import Optional

from fastapi import APIRouter, Header, HTTPException, Query
from psycopg2.extras import execute_values

from api import db, scraper
from api.config import SCRAPER_KEY, secret_matches
from api.optimizer import optimize
from api.sources import VTEX_REALTIME_STORES, min_price_for, sepa_get, vtex_offer, vtex_search

log = logging.getLogger(__name__)
router = APIRouter()

_executor = ThreadPoolExecutor(max_workers=8)


def _require_scraper_key(header_key: Optional[str], body_key: Optional[str] = None):
    if not SCRAPER_KEY:
        raise HTTPException(503, "Endpoint deshabilitado")
    if not secret_matches(header_key or body_key, SCRAPER_KEY):
        raise HTTPException(401, "Unauthorized")


def _vtex_realtime_all(q: str, max_per_store: int) -> dict[str, list]:
    """Busca `q` en todas las cadenas VTEX en paralelo: {cadena: [productos]}."""
    futures = {cad: _executor.submit(vtex_search, url, q, 0, 9, 8) for cad, url in VTEX_REALTIME_STORES.items()}
    return {cad: f.result()[:max_per_store] for cad, f in futures.items()}


def _valid_vtex(p: dict, cadena: str) -> tuple[float, str] | None:
    offer = vtex_offer(p)
    if not offer:
        return None
    price, stock, image = offer
    if price < min_price_for(cadena) or stock <= 0:
        return None
    return price, image


@router.get("/catalog/productos")
def catalog_search(q: str = Query(..., min_length=2), marca: str = Query(None), cadena: str = Query(None),
                   limit: int = Query(30, le=100)):
    if not db.enabled():
        return []
    q_lower = q.lower().strip()
    words = q_lower.split()
    if not words:
        return []
    conditions = ["nombre_lower LIKE %s" for _ in words]
    params: list = [f"%{w}%" for w in words]
    sql = ("SELECT id,nombre,marca,presentacion,cadena,precio,precio_lista,imagen FROM vtex_productos WHERE "
           + " AND ".join(conditions))
    if marca:
        sql += " AND marca_lower LIKE %s"
        params.append(f"%{marca.lower().strip()}%")
    if cadena:
        sql += " AND cadena = %s"
        params.append(cadena)
    sql += " ORDER BY CASE WHEN nombre_lower LIKE %s THEN 0 ELSE 1 END, precio ASC LIMIT %s"
    params.extend([f"{q_lower}%", limit])
    with db.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()
    results = [{"id": r[0], "nombre": r[1], "marca": r[2], "presentacion": r[3], "cadena": r[4], "precio": r[5],
                "precioLista": r[6], "imagen": r[7]} for r in rows if r[5] >= min_price_for(r[4])]

    if len(results) < 5 and not cadena:
        existing = {r["id"] for r in results}
        sepa_future = _executor.submit(_sepa_search_prices, q, words, marca)
        for cad, products in _vtex_realtime_all(q, 10).items():
            for p in products:
                doc_id = f"{cad}_{p.get('productId', '')}"
                name, brand = p.get("productName", ""), p.get("brand", "")
                if doc_id in existing or not all(w in name.lower() for w in words):
                    continue
                if marca and marca.lower() not in brand.lower():
                    continue
                valid = _valid_vtex(p, cad)
                if not valid:
                    continue
                results.append({"id": doc_id, "nombre": name, "marca": brand, "presentacion": "", "cadena": cad,
                                "precio": valid[0], "precioLista": valid[0], "imagen": valid[1]})
                existing.add(doc_id)
        for item in sepa_future.result():
            if item["id"] not in existing:
                results.append(item)
                existing.add(item["id"])

    return results[:limit]


def _sepa_search_prices(q: str, words: list[str], marca: Optional[str]) -> list[dict]:
    """Fallback para Coto y otras cadenas sin VTEX."""
    out = []
    data = sepa_get("/productos", {"string": q, "lat": "-34.83", "lng": "-58.39"})
    for sp in (data or {}).get("productos", [])[:10]:
        sp_name, sp_marca = sp.get("nombre", ""), sp.get("marca", "")
        if not all(w in sp_name.lower() for w in words):
            continue
        if marca and marca.lower() not in sp_marca.lower():
            continue
        sp_id = sp.get("id", "")
        pdata = sepa_get("/producto", {"id_producto": sp_id, "lat": "-34.83", "lng": "-58.39"})
        for suc in (pdata or {}).get("sucursales", [])[:5]:
            cad_name = suc.get("banderaDescripcion", "")
            try:
                precio = float(suc.get("preciosProducto", {}).get("precioLista") or 0)
            except (TypeError, ValueError):
                continue
            if precio < 100:
                continue
            out.append({"id": f"SEPA_{sp_id}_{cad_name[:10]}", "nombre": sp_name, "marca": sp_marca,
                        "presentacion": sp.get("presentacion", ""), "cadena": cad_name,
                        "precio": precio, "precioLista": precio, "imagen": ""})
    return out


@router.get("/catalog/cadenas")
def catalog_cadenas():
    if not db.enabled():
        return []
    with db.cursor() as cur:
        cur.execute("SELECT DISTINCT cadena FROM vtex_productos ORDER BY cadena")
        return [r[0] for r in cur.fetchall() if r[0]]


@router.get("/catalog/promos")
def catalog_promos(cadena: str = Query(None)):
    if not db.enabled():
        return []
    cols = "id,cadena,banco,tarjeta,descuento_pct,dia_semana,tope_reintegro,condiciones"
    with db.cursor() as cur:
        if cadena:
            cur.execute(f"SELECT {cols} FROM promos_bancarias WHERE LOWER(cadena) LIKE %s ORDER BY descuento_pct DESC",
                        (f"%{cadena.lower()}%",))
        else:
            cur.execute(f"SELECT {cols} FROM promos_bancarias ORDER BY cadena,descuento_pct DESC")
        rows = cur.fetchall()
    return [{"id": r[0], "cadena": r[1], "banco": r[2], "tarjeta": r[3], "descuentoPct": r[4], "diaSemana": r[5],
             "topeReintegro": r[6], "condiciones": r[7]} for r in rows]


@router.get("/catalog/marcas")
def catalog_marcas(q: str = Query(None)):
    if not db.enabled():
        return []
    with db.cursor() as cur:
        if q:
            cur.execute("SELECT DISTINCT marca FROM vtex_productos WHERE marca_lower LIKE %s ORDER BY marca LIMIT 20",
                        (f"%{q.lower()}%",))
        else:
            cur.execute("SELECT DISTINCT marca FROM vtex_productos ORDER BY marca LIMIT 50")
        return [r[0] for r in cur.fetchall() if r[0]]


@router.get("/catalog/status")
def catalog_status():
    if not db.enabled():
        return {"lastRun": "", "totalProducts": 0}
    with db.cursor() as cur:
        cur.execute("SELECT ts,duration,products FROM scraper_runs ORDER BY ts DESC LIMIT 1")
        row = cur.fetchone()
        cur.execute("SELECT COUNT(*), COUNT(DISTINCT cadena) FROM vtex_productos")
        tp, nc = cur.fetchone()
    return {"lastRun": str(row[0]) if row else "", "totalProducts": tp, "totalCadenas": nc,
            "scraperRunning": scraper.is_running()}


@router.post("/catalog/scrape")
def catalog_trigger_scrape(x_api_key: Optional[str] = Header(None)):
    """Dispara el scraper manualmente. Requiere header X-Api-Key == SCRAPER_KEY."""
    _require_scraper_key(x_api_key)
    if scraper.is_running():
        return {"status": "already_running"}
    threading.Thread(target=scraper.run_catalog_scraper, daemon=True).start()
    return {"status": "started"}


@router.post("/catalog/import_products")
def catalog_import_products(body: dict, x_api_key: Optional[str] = Header(None)):
    """Importa productos desde un scraper externo (Raspberry Pi).
    Clave en header X-Api-Key (o `api_key` en el body, por compatibilidad)."""
    _require_scraper_key(x_api_key, body.get("api_key"))
    if not db.enabled():
        raise HTTPException(503, "DB not configured")
    rows, skipped = [], 0
    for p in body.get("products", []):
        try:
            precio = float(p["precio"])
            if precio <= 0:
                raise ValueError
            rows.append((str(p["id"]), p["nombre"], p["marca"], p.get("presentacion", ""), p["nombre"].lower(),
                         p["marca"].lower(), p["cadena"], precio, precio, ""))
        except (KeyError, TypeError, ValueError, AttributeError):
            skipped += 1
    rows = list({r[0]: r for r in rows}.values())
    if rows:
        with db.cursor(commit=True) as cur:
            execute_values(cur, """INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,
                cadena,precio,precio_lista,imagen) VALUES %s ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio,
                cadena=EXCLUDED.cadena,nombre=EXCLUDED.nombre,marca=EXCLUDED.marca,updated_at=NOW()""", rows)
    return {"imported": len(rows), "skipped": skipped}


@router.get("/catalog/medios_pago")
def catalog_medios_pago():
    if not db.enabled():
        return []
    with db.cursor() as cur:
        cur.execute("SELECT id,nombre,tipo,nombre_display,tarjetas_disponibles,activo FROM medios_pago "
                    "WHERE activo=TRUE ORDER BY tipo,nombre")
        rows = cur.fetchall()
    return [{"id": r[0], "nombre": r[1], "tipo": r[2], "nombreDisplay": r[3],
             "tarjetasDisponibles": r[4].split(",") if r[4] else None, "activo": r[5]} for r in rows]


@router.get("/catalog/buscar_opciones")
def catalog_buscar_opciones(q: str = Query(..., min_length=2)):
    """Opciones de producto agrupadas por nombre+marca con precio por cadena."""
    if not db.enabled():
        return []
    q_lower = q.lower().strip()
    words = q_lower.split()
    if not words:
        return []
    conditions = " AND ".join(["nombre_lower LIKE %s"] * len(words))
    with db.cursor() as cur:
        cur.execute(f"""SELECT nombre,marca,presentacion,cadena,id,precio FROM vtex_productos
            WHERE {conditions}
            ORDER BY CASE WHEN nombre_lower LIKE %s THEN 0 ELSE 1 END, precio ASC
            LIMIT 200""", [f"%{w}%" for w in words] + [f"{q_lower}%"])
        rows = cur.fetchall()
    groups: OrderedDict = OrderedDict()
    for nombre, marca, pres, cadena, pid, precio in rows:
        if precio < min_price_for(cadena):
            continue
        key = f"{nombre}|{marca}"
        if key not in groups:
            groups[key] = {"nombre": nombre, "marca": marca, "presentacion": pres, "preciosPorCadena": []}
        groups[key]["preciosPorCadena"].append({"cadena": cadena, "productoId": pid, "precio": precio})

    if len(groups) < 5:
        existing = {g["nombre"].lower() for g in groups.values()}
        for cad, products in _vtex_realtime_all(q, 5).items():
            for p in products:
                name, brand = p.get("productName", ""), p.get("brand", "")
                if name.lower() in existing:
                    continue
                valid = _valid_vtex(p, cad)
                if not valid:
                    continue
                key = f"{name}|{brand}"
                if key not in groups:
                    groups[key] = {"nombre": name, "marca": brand, "presentacion": "", "preciosPorCadena": []}
                groups[key]["preciosPorCadena"].append(
                    {"cadena": cad, "productoId": f"{cad}_{p.get('productId', '')}", "precio": valid[0]})
                existing.add(name.lower())
    return list(groups.values())[:50]


class _DbLookup:
    """Implementacion de optimizer.PriceLookup sobre Postgres + VTEX en vivo."""

    def __init__(self, cur):
        self.cur = cur

    def by_id(self, product_id):
        self.cur.execute("SELECT nombre,marca,precio,cadena FROM vtex_productos WHERE id=%s", (product_id,))
        return self.cur.fetchone()

    def same_product(self, cadena, nombre, marca):
        self.cur.execute("SELECT nombre,marca,precio FROM vtex_productos WHERE cadena=%s AND nombre_lower=%s "
                         "AND COALESCE(marca_lower,'')=%s ORDER BY precio LIMIT 1",
                         (cadena, (nombre or "").lower(), (marca or "").lower()))
        return self.cur.fetchone()

    def candidates(self, cadena, nombre):
        nombre_lower = nombre.lower().strip()
        words = nombre_lower.split()
        if not words:
            return []
        conditions = " AND ".join(["nombre_lower LIKE %s"] * len(words))
        self.cur.execute(f"""SELECT nombre,marca,precio FROM vtex_productos
            WHERE cadena=%s AND {conditions}
            ORDER BY CASE WHEN nombre_lower LIKE %s THEN 0 WHEN nombre_lower LIKE %s THEN 1 ELSE 2 END, precio
            LIMIT 10""", [cadena] + [f"%{w}%" for w in words] + [f"{nombre_lower}%", f"{words[0]}%"])
        return [r for r in self.cur.fetchall() if r[2] >= min_price_for(cadena)]

    def realtime(self, cadena, nombre):
        base = VTEX_REALTIME_STORES.get(cadena)
        if not base:
            return None
        best = None
        for p in vtex_search(base, nombre, 0, 9, 10):
            valid = _valid_vtex(p, cadena)
            if valid and (best is None or valid[0] < best[2]):
                best = (p.get("productName", ""), p.get("brand", ""), valid[0])
        return best


@router.post("/catalog/optimizar")
def catalog_optimizar(body: dict):
    """Calcula la cadena optima y la distribucion de pagos para una lista de compras."""
    if not db.enabled():
        raise HTTPException(503, "DB not configured")
    medios_ids = body.get("medios_pago_ids", [])
    with db.cursor() as cur:
        cur.execute("SELECT DISTINCT cadena FROM vtex_productos")
        cadenas = [r[0] for r in cur.fetchall() if r[0]]
        promo_sql = ("SELECT cadena,banco,tarjeta,descuento_pct,dia_semana,tope_reintegro,medio_pago_id "
                     "FROM promos_bancarias")
        if medios_ids:
            cur.execute(promo_sql + " WHERE medio_pago_id IN %s", (tuple(medios_ids),))
        else:
            cur.execute(promo_sql)
        promos = cur.fetchall()
        return optimize(
            productos=body.get("productos", []), cadenas=cadenas, promos=promos, lookup=_DbLookup(cur),
            dia=body.get("dia_semana", ""), tarjetas_sel=body.get("tarjetas_seleccionadas", {}),
            cadenas_filter=body.get("cadenas", []),
        )
