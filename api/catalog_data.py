"""Datos semilla del catalogo: promos bancarias, medios de pago, zonas y terminos a scrapear."""

PROMOS_SEMILLA = [  # (cadena, banco, tarjeta, pct, dia, tope, vig_desde, vig_hasta, condiciones)
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

BANCOS = [  # (nombre, tarjetas)
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
BILLETERAS = [
    "Apple Pay","Astropay","Axion ON","Billetera Santa Fe","BNA+","Cenco Pay",
    "Claro Pay","Cuenta DNI","GoCuotas","Lemon","Mercado Pago","MODO",
    "n1u","Nubi","Personal Pay","Prex","Shell Box","Ualá","Billetera Córdoba",
    "Wayni","Wibond","Naranja X","Tap","YOY",
]
CLUBES = [
    "¡Appa!","Beneficios La Capital","Club Crónica","Club La Nación",
    "Automóvil Club Argentino (ACA)","Clarín 365","Club DIA","Comunidad Coto","Tarjeta Cencosud",
]

ZONAS = [  # (nombre, lat, lng, radio_km)
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

SCRAPE_TERMS = [
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

SCHEMA_SQL = [
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
]
