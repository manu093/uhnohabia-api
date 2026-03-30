import firebase_admin, json, os
from firebase_admin import credentials, firestore

# Load service account
sa_path = os.path.join(os.path.dirname(__file__), "serviceAccountKey.json")
if os.path.exists(sa_path):
    cred = credentials.Certificate(sa_path)
elif os.environ.get("FIREBASE_SERVICE_ACCOUNT"):
    sa = json.loads(os.environ["FIREBASE_SERVICE_ACCOUNT"])
    cred = credentials.Certificate(sa)
else:
    raise Exception("No credentials found")

firebase_admin.initialize_app(cred)
db = firestore.client()

uid = "P46tbJbTrzQHGocCqI4CgRJwHah2"

products = [
    ("Poet", "🧹", "Limpieza"),
    ("Lavandina", "🧴", "Limpieza"),
    ("Pastillas mosquitos", "🦟", "Limpieza"),
    ("Galles Max", "🍪", "Almacén"),
    ("Leche", "🥛", "Granja"),
    ("Crema para el pelo", "🧴", "Perfumería"),
    ("Tapas empanadas", "🥟", "Almacén"),
    ("Tapa tarta", "🥧", "Almacén"),
    ("Baño de crema", "🧴", "Perfumería"),
    ("Atún", "🐟", "Almacén"),
    ("Dulce de leche", "🍯", "Almacén"),
    ("Merengues", "🍬", "Almacén"),
    ("Cebolla", "🧅", "Verdulería"),
    ("Zanahoria", "🥕", "Verdulería"),
    ("Morrón", "🫑", "Verdulería"),
    ("Papa", "🥔", "Verdulería"),
    ("Cebolla verdeo", "🧅", "Verdulería"),
    ("Perejil", "🌿", "Verdulería"),
    ("Berenjena", "🍆", "Verdulería"),
    ("Lechuga", "🥬", "Verdulería"),
    ("Repollo", "🥬", "Verdulería"),
    ("Papel manteca", "📜", "Almacén"),
    ("Folex", "🍽️", "Limpieza"),
    ("Bicarbonato de sodio", "🧂", "Almacén"),
    ("Polvo de hornear", "🧁", "Almacén"),
    ("Ariel", "🧹", "Limpieza"),
    ("Antimanchas", "🧴", "Limpieza"),
    ("Queso crema", "🧀", "Granja"),
    ("Tomate cartón", "🍅", "Almacén"),
    ("Tallarines", "🍝", "Almacén"),
    ("Arroz común", "🍚", "Almacén"),
    ("Mate cocido", "🧉", "Almacén"),
    ("Pollo", "🐔", "Carnicería"),
    ("Zapallo", "🎃", "Verdulería"),
    ("Queso", "🧀", "Granja"),
    ("Bife", "🥩", "Carnicería"),
    ("Carne picada", "🥩", "Carnicería"),
    ("Matambre de carne", "🥩", "Carnicería"),
    ("Suprema", "🐔", "Carnicería"),
]

for name, emoji, cat in products:
    doc_id = f"{uid}_{name}"
    data = {
        "ownerId": uid,
        "name": name,
        "emoji": emoji,
        "categoryName": cat,
        "categoryEmoji": emoji,
        "categoryId": "",
        "defaultUnit": "Unidad"
    }
    db.collection("knownProducts").document(doc_id).set(data)
    print(f"  {emoji} {name} -> {cat}")

print(f"\nCargados {len(products)} productos")
