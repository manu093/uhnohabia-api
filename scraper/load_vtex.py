"""Load products from VTEX APIs into Neon DB."""
import os, psycopg2, requests, time
from psycopg2.extras import execute_values

DB = os.environ.get("DATABASE_URL", "")
STORES = {
    "DIA": "https://diaonline.supermercadosdia.com.ar",
    "Jumbo": "https://www.jumbo.com.ar",
    "Disco": "https://www.disco.com.ar",
    "Carrefour": "https://www.carrefour.com.ar",
    "Changomas": "https://www.masonline.com.ar",
}
TERMS = [
    # Lácteos
    "leche entera", "leche descremada", "leche larga vida", "leche chocolatada",
    "leche UAT", "leche deslactosada", "leche en polvo",
    "yogur", "yogur bebible", "yogur griego",
    "queso cremoso", "queso rallado", "queso untable", "queso crema",
    "queso barra", "queso port salut", "queso sardo", "queso pategras",
    "manteca", "crema de leche", "dulce de leche", "postre", "ricotta",
    # Almacén
    "arroz", "arroz doble carolina", "arroz parboil", "arroz integral",
    "fideos", "fideos spaghetti", "fideos mostachol", "fideos tallarín",
    "aceite girasol", "aceite oliva", "aceite mezcla", "harina", "harina leudante",
    "azucar", "sal fina", "sal gruesa", "yerba mate", "cafe", "cafe molido", "te",
    "galletitas", "galletitas dulces", "galletitas saladas",
    "pan lactal", "pan integral", "pan rallado", "tostadas", "grisines",
    "mermelada", "miel", "cereales", "avena", "polenta", "lentejas",
    "garbanzos", "porotos", "pure tomate", "tomate perita", "salsa",
    "mayonesa", "mostaza", "ketchup", "vinagre", "caldo",
    "atun", "sardinas", "arvejas", "choclo", "aceitunas",
    "pimenton", "pimienta", "oregano", "comino", "provenzal", "chimichurri",
    "aji molido", "curcuma", "nuez moscada", "laurel",
    "tapas empanadas", "tapas pascualina", "tapas tarta",
    "maicena", "levadura", "premezcla", "rebozador",
    "gelatina", "flan", "chocolate", "cacao", "alfajor",
    "dulce batata", "dulce membrillo", "coco rallado",
    "frutos secos", "almendras", "nueces", "pasas de uva", "semillas",
    # Carnes y fiambres
    "pollo", "pechuga pollo", "pata muslo",
    "carne picada", "nalga", "bife", "asado", "matambre",
    "milanesa", "hamburguesa", "nuggets", "medallón",
    "jamon cocido", "jamon crudo", "salchichas", "salame", "mortadela",
    "chorizo", "bondiola", "panceta", "morcilla", "paté",
    # Frutas y verduras
    "papa", "cebolla", "zanahoria", "lechuga", "tomate",
    "banana", "manzana", "naranja", "limon", "mandarina",
    "pera", "durazno", "uva", "frutilla", "kiwi", "ananá",
    "zapallo", "zapallito", "berenjena", "morron", "pepino",
    "espinaca", "acelga", "brocoli", "coliflor", "batata", "remolacha",
    "ajo", "perejil", "albahaca", "rucula",
    # Bebidas
    "cerveza", "cerveza lata", "vino tinto", "vino blanco",
    "gaseosa", "gaseosa cola", "agua mineral", "soda",
    "jugo", "jugo en polvo", "energizante", "fernet", "aperitivo",
    "bebida isotónica", "tónica",
    # Limpieza
    "detergente", "jabon polvo", "jabon liquido", "lavandina",
    "suavizante", "limpiador", "desengrasante", "trapo piso",
    "esponja", "bolsa residuos", "insecticida", "limpiavidrios",
    "limpiador pisos", "limpiador cocina", "limpiador baño",
    "desodorante ambiente", "cera pisos", "quitamanchas",
    "papel cocina", "papel higienico", "servilletas",
    "cloro", "desinfectante",
    # Higiene personal
    "shampoo", "acondicionador", "jabon tocador", "pasta dental",
    "cepillo dental", "desodorante", "crema corporal",
    "toallitas", "pañales", "protectores diarios", "afeitadora",
    "espuma afeitar", "protector solar", "alcohol", "agua oxigenada",
    "algodón", "curitas", "hilo dental", "enjuague bucal",
    "toallitas femeninas", "tampones",
    # Congelados
    "empanadas", "pizza", "papas fritas congeladas", "helado",
    "vegetales congelados", "milanesa soja", "suprema",
    # Mascotas
    "alimento perro", "alimento gato", "piedras sanitarias",
    # Bebés
    "leche infantil", "papilla",
    # Panadería
    "facturas", "medialunas", "bizcochos", "prepizza",
    "pan hamburguesa", "pan pancho",
    # Huevos
    "huevos",
    # Bazar y hogar
    "broches ropa", "pinzas ropa", "fósforos", "velas",
    "papel aluminio", "film", "bolsa freezer", "pilas", "lamparita",
    "foco led", "contenedor plástico",
    # Varios
    "pañuelos descartables", "guantes descartables",
    "carbón", "leña", "encendedor",
]
HEADERS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}

total = 0
for cadena, base in STORES.items():
    count = 0
    for term in TERMS:
        try:
            r = requests.get(
                f"{base}/api/catalog_system/pub/products/search/{term}",
                params={"_from": 0, "_to": 49}, headers=HEADERS, timeout=15
            )
            if r.status_code not in (200, 206):
                continue
            data = r.json()
            if not isinstance(data, list):
                continue
            rows = []
            for p in data:
                pid = p.get("productId", "")
                if not pid:
                    continue
                name = p.get("productName", "")
                brand = p.get("brand", "")
                price, lprice, img = 0.0, 0.0, ""
                try:
                    item = p["items"][0]
                    offer = item["sellers"][0]["commertialOffer"]
                    price = offer.get("Price", 0)
                    lprice = offer.get("ListPrice", price)
                    # Ensure they're proper numbers
                    price = float(price) if price else 0.0
                    lprice = float(lprice) if lprice else 0.0
                    imgs = item.get("images", [])
                    img = imgs[0]["imageUrl"] if imgs else ""
                except:
                    pass
                if price <= 0:
                    continue
                if price < 100:
                    continue
                # Fix ListPrice bug in Jumbo/Disco (sometimes 100x the real price)
                if lprice > price * 3:
                    lprice = price
                rows.append((
                    f"{cadena}_{pid}", name, brand, "",
                    name.lower(), brand.lower(), cadena,
                    price, lprice, img
                ))
            if rows:
                cn = psycopg2.connect(DB)
                cr = cn.cursor()
                execute_values(cr, """
                    INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
                    VALUES %s ON CONFLICT (id) DO UPDATE SET
                    nombre=EXCLUDED.nombre,marca=EXCLUDED.marca,precio=EXCLUDED.precio,
                    precio_lista=EXCLUDED.precio_lista,imagen=EXCLUDED.imagen,updated_at=NOW()
                """, rows)
                cn.commit()
                cr.close()
                cn.close()
                count += len(rows)
        except Exception as e:
            print(f"  Error {cadena}/{term}: {e}")
        time.sleep(0.5)
    print(f"{cadena}: {count} products")
    total += count
print(f"Total: {total}")


# Load Coto via their Endeca/ATG JSON API
print("\nLoading Coto...")
import json as _json
coto_count = 0
COTO_BASE = "https://www.cotodigital.com.ar/sitios/cdigi/browse"
for term in TERMS:
    try:
        r = requests.get(COTO_BASE, params={
            "_dyncharset": "utf-8", "Dy": "1", "Ntt": term,
            "Nty": "1", "Ntk": "product.displayName",
            "format": "json", "pushSite": "CotoDigital"
        }, headers=HEADERS, timeout=15)
        if r.status_code != 200:
            continue
        data = r.json()
        # Navigate to records
        records = None
        for content in data.get("contents", [{}]):
            for mc in content.get("MainContent", content.get("Main", [])):
                for inner in mc.get("contents", [mc]):
                    if "records" in inner and inner["records"]:
                        records = inner["records"]
                        break
        if not records:
            continue
        rows = []
        for rec in records:
            sub = rec.get("records", [{}])
            attrs = sub[0].get("attributes", {}) if sub else rec.get("attributes", {})
            name = (attrs.get("product.displayName", [""])[0])
            brand = (attrs.get("product.brand", attrs.get("product.MARCA", [""]))[0])
            price_str = (attrs.get("sku.activePrice", ["0"])[0])
            img = (attrs.get("product.mediumImage.url", [""])[0])
            try:
                price = float(price_str)
            except:
                continue
            if price < 100:
                continue
            pid = attrs.get("product.repositoryId", [""])[0]
            if not pid:
                continue
            lprice = price
            try:
                dto_json = attrs.get("sku.dtoPrice", ["{}"])[0]
                dto = _json.loads(dto_json)
                lprice = float(dto.get("precioLista", price))
            except:
                pass
            if lprice > price * 3:
                lprice = price
            rows.append((f"Coto_{pid}", name, brand, "", name.lower(), brand.lower(), "Coto", price, lprice, img))
        if rows:
            cn = psycopg2.connect(DB)
            cr = cn.cursor()
            execute_values(cr, """INSERT INTO vtex_productos (id,nombre,marca,presentacion,nombre_lower,marca_lower,cadena,precio,precio_lista,imagen)
                VALUES %s ON CONFLICT (id) DO UPDATE SET precio=EXCLUDED.precio,precio_lista=EXCLUDED.precio_lista,updated_at=NOW()""", rows)
            cn.commit(); cr.close(); cn.close()
            coto_count += len(rows)
    except Exception as e:
        print(f"  Error Coto/{term}: {e}")
    time.sleep(0.5)
print(f"Coto: {coto_count} products")
total += coto_count


# Final cleanup: remove any remaining invalid prices
print("\nFinal cleanup...")
cn = psycopg2.connect(DB); cr = cn.cursor()
cr.execute("DELETE FROM vtex_productos WHERE precio < 100")
cn.commit()
print(f"Removed {cr.rowcount} products with price < $100")
cr.execute("UPDATE vtex_productos SET precio_lista = precio WHERE precio_lista > precio * 3")
cn.commit()
print(f"Fixed ListPrice for {cr.rowcount} products")
cr.execute("SELECT COUNT(*) FROM vtex_productos")
print(f"Total products: {cr.fetchone()[0]}")
cr.close(); cn.close()
print(f"\nGrand total loaded: {total}")
