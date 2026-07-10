"""Full catalog scraper: fetches ALL products by walking category trees.
Works with VTEX stores (DIA, Jumbo, Disco, Carrefour, Changomas) and Coto (Endeca).
Designed to run on Docker (local server) daily at 6AM.

Strategy:
- Get category tree from each VTEX store
- For each category, check total products via resources header
- If total <= 2500, paginate directly
- If total > 2500, drill into subcategories
- For Coto, use Endeca JSON API with broad search terms + pagination
"""
import os, sys, psycopg2, requests, time, json, logging
from psycopg2.extras import execute_values

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("full_catalog")

DB = os.environ.get("DATABASE_URL", "")
if not DB:
    log.error("DATABASE_URL not set"); sys.exit(1)

HEADERS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}
RATE_LIMIT = 0.5  # seconds between requests

VTEX_STORES = {
    "DIA": "https://diaonline.supermercadosdia.com.ar",
    "Jumbo": "https://www.jumbo.com.ar",
    "Disco": "https://www.disco.com.ar",
    "Carrefour": "https://www.carrefour.com.ar",
    "Changomas": "https://www.masonline.com.ar",
}

# Categories to skip (non-food, electronics, clothing, etc.)
SKIP_KEYWORDS = [
    "electro", "tecnología", "tecnologia", "indumentaria", "calzado",
    "colchon", "sommier", "mueble", "test category", "(old)", "mercadolibre",
    "combo", "camping", "deporte", "juguete", "librería",
    "automotor", "ferretería", "pileta", "bicicleta",
]


def pg():
    return psycopg2.connect(DB)

def should_skip(name):
    n = name.lower().strip()
    return any(kw in n for kw in SKIP_KEYWORDS)


def get_category_tree(base_url):
    """Get full category tree from VTEX store."""
    try:
        r = requests.get(f"{base_url}/api/catalog_system/pub/category/tree/3",
                         headers=HEADERS, timeout=15)
        if r.status_code == 200:
            return r.json()
    except Exception as e:
        log.error(f"Failed to get category tree from {base_url}: {e}")
    return []


def get_category_total(base_url, category_id):
    """Check how many products a category has via the resources header."""
    try:
        r = requests.get(f"{base_url}/api/catalog_system/pub/products/search",
            params={"fq": f"C:/{category_id}/", "_from": 0, "_to": 0},
            headers=HEADERS, timeout=15)
        res = r.headers.get("resources", "0-0/0")
        total = int(res.split("/")[-1])
        return total
    except:
        return 0


def fetch_products_for_category(base_url, category_id, cadena, max_products=2500):
    """Fetch all products from a VTEX category with pagination."""
    products = []
    page_size = 50
    offset = 0

    while offset < max_products:
        try:
            r = requests.get(f"{base_url}/api/catalog_system/pub/products/search",
                params={"fq": f"C:/{category_id}/", "_from": offset, "_to": offset + page_size - 1},
                headers=HEADERS, timeout=20)
            if r.status_code not in (200, 206):
                break
            data = r.json()
            if not isinstance(data, list) or len(data) == 0:
                break
            for p in data:
                row = parse_vtex_product(p, cadena)
                if row:
                    products.append(row)
            if len(data) < page_size:
                break
            offset += page_size
            time.sleep(RATE_LIMIT)
        except Exception as e:
            log.warning(f"Error fetching {cadena} cat {category_id} offset {offset}: {e}")
            break
    return products


def parse_vtex_product(p, cadena):
    """Parse a VTEX product JSON into a DB row tuple."""
    pid = p.get("productId", "")
    if not pid:
        return None
    name = p.get("productName", "")
    brand = p.get("brand", "")
    price, lprice, img = 0.0, 0.0, ""
    try:
        item = p["items"][0]
        offer = item["sellers"][0]["commertialOffer"]
        price = float(offer.get("Price", 0))
        lprice = float(offer.get("ListPrice", price))
        # Skip products with no stock (discontinued, stale prices)
        avail = int(offer.get("AvailableQuantity", 0))
        if avail <= 0:
            return None
        imgs = item.get("images", [])
        img = imgs[0]["imageUrl"] if imgs else ""
    except:
        pass
    if price < 100:
        return None
    # Jumbo/Disco sometimes return price per gram/ml instead of per unit
    # Use higher minimum for these chains
    if cadena in ("Jumbo", "Disco") and price < 1000:
        return None
    if lprice > price * 3:
        lprice = price
    return (f"{cadena}_{pid}", name, brand, "",
            name.lower(), brand.lower(), cadena, price, lprice, img)


def save_products(products):
    """Bulk insert/update products into DB."""
    if not products:
        return
    # Deduplicate by ID (first column), keeping last occurrence
    seen = {}
    for row in products:
        seen[row[0]] = row
    deduped = list(seen.values())
    cn = pg(); cr = cn.cursor()
    execute_values(cr, """
        INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
        VALUES %s ON CONFLICT (id) DO UPDATE SET
        nombre=EXCLUDED.nombre, marca=EXCLUDED.marca, precio=EXCLUDED.precio,
        precio_lista=EXCLUDED.precio_lista, imagen=EXCLUDED.imagen,
        nombre_lower=EXCLUDED.nombre_lower, marca_lower=EXCLUDED.marca_lower, updated_at=NOW()
    """, deduped)
    cn.commit(); cr.close(); cn.close()


def scrape_category_recursive(base_url, category, cadena, depth=0):
    """Scrape a category. If it has >2500 products, drill into children."""
    cid = category["id"]
    name = category.get("name", "")
    if should_skip(name):
        return 0

    total_in_cat = get_category_total(base_url, cid)
    time.sleep(RATE_LIMIT)

    if total_in_cat == 0:
        # Try children even if parent shows 0 (DIA pattern)
        children = category.get("children", [])
        if children:
            count = 0
            for child in children:
                count += scrape_category_recursive(base_url, child, cadena, depth + 1)
            return count
        return 0

    if total_in_cat <= 2500:
        # Can fetch directly - save in batches to avoid duplicate issues
        products = fetch_products_for_category(base_url, cid, cadena, min(total_in_cat, 2500))
        saved = 0
        for i in range(0, len(products), 500):
            batch = products[i:i+500]
            save_products(batch)
            saved += len(batch)
        indent = "  " * (depth + 1)
        log.info(f"{indent}{name} ({cid}): {saved}/{total_in_cat} products")
        return saved
    else:
        # Too many products, drill into subcategories
        children = category.get("children", [])
        if not children:
            # No children but >2500 products - fetch what we can (first 2500)
            products = fetch_products_for_category(base_url, cid, cadena, 2500)
            for i in range(0, len(products), 500):
                save_products(products[i:i+500])
            log.info(f"{'  '*(depth+1)}{name} ({cid}): {len(products)}/{total_in_cat} products (capped at 2500)")
            return len(products)
        count = 0
        for child in children:
            count += scrape_category_recursive(base_url, child, cadena, depth + 1)
        # Check if we missed products by comparing
        if count < total_in_cat * 0.5:
            # Children didn't return enough, fetch from parent directly
            products = fetch_products_for_category(base_url, cid, cadena, 2500)
            for i in range(0, len(products), 500):
                save_products(products[i:i+500])
            count = max(count, len(products))
        return count


def scrape_vtex_store(cadena, base_url):
    """Scrape entire VTEX store by walking its category tree."""
    log.info(f"=== Scraping {cadena} ({base_url}) ===")
    tree = get_category_tree(base_url)
    if not tree:
        log.error(f"  No category tree found for {cadena}")
        return 0
    log.info(f"  Found {len(tree)} top-level categories")

    total = 0
    for cat in tree:
        try:
            count = scrape_category_recursive(base_url, cat, cadena)
            total += count
        except Exception as e:
            log.error(f"  Error in category {cat.get('name','')}: {e}")
        time.sleep(1)  # pause between top-level categories

    log.info(f"  {cadena} TOTAL: {total} products")
    return total


def scrape_coto():
    """Scrape Coto via their Endeca/ATG JSON API with broad vowel searches for full coverage."""
    log.info("=== Scraping Coto ===")
    COTO_BASE = "https://www.cotodigital.com.ar/sitios/cdigi/browse"

    # Use single-letter and common prefix searches to cover the entire catalog
    # Coto's Endeca returns up to ~1000 results per search with pagination
    SEARCH_TERMS = list("abcdefghijklmnopqrstuvwxyz") + [
        "leche", "arroz", "fideos", "aceite", "queso", "yogur",
        "galletitas", "pan", "cerveza", "vino", "gaseosa", "agua",
        "detergente", "shampoo", "jabón", "carne", "pollo", "jamón",
        "mayonesa", "yerba", "café", "papel", "pañales", "limpiador",
    ]

    all_seen = set()
    total = 0

    for term in SEARCH_TERMS:
        page = 0
        max_pages = 40  # 40 pages * 24 = 960 products per term
        while page < max_pages:
            try:
                offset = page * 24
                r = requests.get(COTO_BASE, params={
                    "_dyncharset": "utf-8", "Dy": "1", "Ntt": term,
                    "Nty": "1", "Ntk": "product.displayName",
                    "No": str(offset), "Nrpp": "24",
                    "format": "json", "pushSite": "CotoDigital"
                }, headers=HEADERS, timeout=15)
                if r.status_code != 200:
                    break
                all_attrs = _find_all_attrs(r.json())
                if not all_attrs:
                    break
                new_in_page = 0
                rows = []
                for attrs in all_attrs:
                    name = attrs.get("product.displayName", [""])[0]
                    brand = attrs.get("product.brand", attrs.get("product.MARCA", [""]))[0]
                    price_str = attrs.get("sku.activePrice", ["0"])[0]
                    img = attrs.get("product.mediumImage.url", [""])[0]
                    pid = attrs.get("product.repositoryId", [""])[0]
                    try:
                        price = float(price_str)
                    except:
                        continue
                    if price < 100 or not pid:
                        continue
                    doc_id = f"Coto_{pid}"
                    if doc_id in all_seen:
                        continue
                    all_seen.add(doc_id)
                    new_in_page += 1
                    lprice = price
                    try:
                        dto = json.loads(attrs.get("sku.dtoPrice", ["{}"])[0])
                        lprice = float(dto.get("precioLista", price))
                    except:
                        pass
                    if lprice > price * 3:
                        lprice = price
                    rows.append((doc_id, name, brand, "", name.lower(), brand.lower(),
                                 "Coto", price, lprice, img))
                if rows:
                    save_products(rows)
                    total += len(rows)
                # If no new products on this page, stop paginating this term
                if new_in_page == 0 or len(all_attrs) < 20:
                    break
                page += 1
                time.sleep(RATE_LIMIT)
            except Exception as e:
                log.warning(f"Error Coto/{term} page {page}: {e}")
                break
        time.sleep(RATE_LIMIT)
        if total > 0 and total % 500 < 50:
            log.info(f"  Coto progress: {total} products ({len(all_seen)} unique)")

    log.info(f"  Coto TOTAL: {total} products")
    return total


def _find_all_attrs(obj, depth=0):
    """Recursively find product attributes in Coto's Endeca JSON."""
    results = []
    if depth > 10:
        return results
    if isinstance(obj, dict):
        if "records" in obj and isinstance(obj["records"], list):
            for rec in obj["records"]:
                sub = rec.get("records", [])
                if sub:
                    for s in sub:
                        a = s.get("attributes", {})
                        if a:
                            results.append(a)
                else:
                    a = rec.get("attributes", {})
                    if a and "sku.activePrice" in a:
                        results.append(a)
        for v in obj.values():
            results.extend(_find_all_attrs(v, depth + 1))
    elif isinstance(obj, list):
        for item in obj:
            results.extend(_find_all_attrs(item, depth + 1))
    return results


def cleanup_db():
    """Remove invalid products and fix price anomalies."""
    log.info("Running DB cleanup...")
    cn = pg(); cr = cn.cursor()
    cr.execute("DELETE FROM vtex_productos WHERE precio < 100")
    log.info(f"  Removed {cr.rowcount} products with price < $100")
    # Remove Jumbo/Disco products with suspiciously low prices (price-per-unit artifacts)
    cr.execute("DELETE FROM vtex_productos WHERE cadena IN ('Jumbo','Disco') AND precio < 1000")
    log.info(f"  Removed {cr.rowcount} Jumbo/Disco products with price < $500")
    # Remove Carrefour discontinued products (low price + likely no stock)
    cr.execute("DELETE FROM vtex_productos WHERE cadena='Carrefour' AND precio < 500 AND nombre_lower NOT LIKE '%x kg%'")
    log.info(f"  Removed {cr.rowcount} Carrefour products with price < $500")
    cr.execute("UPDATE vtex_productos SET precio_lista = precio WHERE precio_lista > precio * 3")
    log.info(f"  Fixed ListPrice for {cr.rowcount} products")
    # Remove products not updated in 7 days (likely discontinued)
    cr.execute("DELETE FROM vtex_productos WHERE updated_at < NOW() - INTERVAL '7 days'")
    log.info(f"  Removed {cr.rowcount} stale products (>7 days old)")
    cn.commit()
    cr.execute("SELECT COUNT(*) FROM vtex_productos")
    total = cr.fetchone()[0]
    cr.execute("SELECT cadena, COUNT(*) FROM vtex_productos GROUP BY cadena ORDER BY cadena")
    breakdown = cr.fetchall()
    cr.close(); cn.close()
    log.info(f"  Total products in DB: {total}")
    for cadena, cnt in breakdown:
        log.info(f"    {cadena}: {cnt}")
    return total


def main():
    start = time.time()
    log.info("========== FULL CATALOG SCRAPER START ==========")

    grand_total = 0
    for cadena, base_url in VTEX_STORES.items():
        try:
            count = scrape_vtex_store(cadena, base_url)
            grand_total += count
        except Exception as e:
            log.error(f"Failed to scrape {cadena}: {e}")
        time.sleep(2)

    try:
        coto_count = scrape_coto()
        grand_total += coto_count
    except Exception as e:
        log.error(f"Failed to scrape Coto: {e}")

    total_in_db = cleanup_db()

    duration = time.time() - start
    log.info(f"========== DONE: {grand_total} products scraped, {total_in_db} in DB, took {duration:.0f}s ==========")

    # Save run status
    try:
        cn = pg(); cr = cn.cursor()
        cr.execute("INSERT INTO scraper_runs (duration,products,prices) VALUES (%s,%s,%s)",
                   (duration, grand_total, grand_total))
        cn.commit(); cr.close(); cn.close()
    except:
        pass


if __name__ == "__main__":
    main()
