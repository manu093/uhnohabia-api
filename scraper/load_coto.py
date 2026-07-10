"""Load Coto products via their Endeca JSON API."""
import os, psycopg2, requests, time, json

DB = os.environ.get("DATABASE_URL", "")
HEADERS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}
COTO_BASE = "https://www.cotodigital.com.ar/sitios/cdigi/browse"
TERMS = [
    "leche entera", "leche descremada", "leche larga vida", "leche UAT",
    "leche chocolatada", "leche en polvo", "arroz", "arroz doble carolina",
    "arroz parboil", "fideos", "fideos spaghetti", "fideos mostachol",
    "aceite girasol", "aceite oliva", "aceite mezcla", "harina", "harina leudante",
    "azucar", "sal fina", "sal gruesa", "yerba mate", "cafe", "cafe molido", "te",
    "galletitas", "galletitas dulces", "galletitas saladas", "pan", "pan lactal",
    "pan rallado", "tostadas", "grisines",
    "manteca", "queso", "queso crema", "queso cremoso", "queso rallado",
    "queso untable", "queso port salut", "yogur", "yogur bebible",
    "huevos", "pollo", "pechuga pollo", "carne", "carne picada", "nalga", "bife",
    "jamon", "jamon crudo", "salchichas", "salame", "mortadela", "chorizo",
    "atun", "sardinas", "tomate", "tomate perita", "mayonesa", "mostaza", "ketchup",
    "cerveza", "cerveza lata", "vino", "vino tinto", "gaseosa", "agua mineral",
    "jugo", "jugo en polvo", "soda", "fernet", "energizante",
    "detergente", "jabon", "jabon liquido", "shampoo", "acondicionador",
    "papel higienico", "papel cocina", "servilletas",
    "lavandina", "desodorante", "pasta dental", "pañales",
    "suavizante", "limpiador", "limpiavidrios", "limpiador pisos",
    "limpiador cocina", "desinfectante", "esponja", "bolsa residuos",
    "crema de leche", "dulce de leche", "mermelada", "miel",
    "cereales", "polenta", "lentejas", "garbanzos", "porotos",
    "empanadas", "pizza", "helado", "hamburguesa", "milanesa", "nuggets",
    "alimento perro", "alimento gato", "piedras sanitarias",
    "insecticida", "desodorante ambiente",
    "postre", "flan", "gelatina", "chocolate", "alfajor",
    "pimenton", "pimienta", "oregano", "comino", "chimichurri", "provenzal",
    "tapas empanadas", "tapas pascualina", "maicena", "levadura",
    "pure tomate", "salsa", "arvejas", "choclo", "aceitunas", "vinagre", "caldo",
    "dulce batata", "dulce membrillo", "coco rallado",
    "broches ropa", "papel aluminio", "film", "pilas", "fósforos", "velas",
    "protector solar", "crema corporal", "alcohol", "algodón",
    "toallitas femeninas", "afeitadora",
    "facturas", "medialunas", "bizcochos", "prepizza",
    "frutos secos", "almendras", "nueces", "semillas",
    "ricotta", "avena", "rebozador", "premezcla",
]

def find_all_attrs(obj, depth=0):
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
            results.extend(find_all_attrs(v, depth + 1))
    elif isinstance(obj, list):
        for item in obj:
            results.extend(find_all_attrs(item, depth + 1))
    return results

count = 0
all_seen = set()

for term in TERMS:
    try:
        r = requests.get(COTO_BASE, params={
            "_dyncharset": "utf-8", "Dy": "1", "Ntt": term,
            "Nty": "1", "Ntk": "product.displayName",
            "format": "json", "pushSite": "CotoDigital"
        }, headers=HEADERS, timeout=15)
        if r.status_code != 200:
            continue
        all_attrs = find_all_attrs(r.json())
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
            lprice = price
            try:
                dto = json.loads(attrs.get("sku.dtoPrice", ["{}"])[0])
                lprice = float(dto.get("precioLista", price))
            except:
                pass
            if lprice > price * 3:
                lprice = price
            # Insert one by one to avoid duplicate issues
            cn = psycopg2.connect(DB)
            cr = cn.cursor()
            cr.execute("""INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio,precio_lista=EXCLUDED.precio_lista,updated_at=NOW()""",
                (doc_id, name, brand, "", name.lower(), brand.lower(), "Coto", price, lprice, img))
            cn.commit()
            cr.close()
            cn.close()
            count += 1
    except Exception as e:
        print(f"Error Coto/{term}: {e}")
    time.sleep(0.5)

print(f"Coto: {count} products loaded")
