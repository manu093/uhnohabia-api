"""Acceso a Firestore para los comandos de voz, con el mismo formato que escribe la app
(timestamps en epoch millis, members, categorias de users/{uid}/categories).

Pensado para responder rapido (Alexa corta a los 8 s): pocas consultas por pedido y
escrituras en lote."""
import time
from dataclasses import dataclass, field

from api.voice_parser import OTROS, VoiceItem, normalize, parse_products

DEFAULT_CATEGORY_EMOJI = {
    "Carnicería": "🥩", "Verdulería": "🥬", "Limpieza": "🧹", "Perfumería": "🧴",
    "Granja": "🥚", "Bebidas": "🥤", "Almacén": "🏪", OTROS: "📦",
}
VOICE_AUTHOR = "Alexa"


def now_ms() -> int:
    return int(time.time() * 1000)


def _millis(value) -> int:
    """updatedAt puede ser int (app) o Timestamp de Firestore (escrito por versiones viejas)."""
    if isinstance(value, (int, float)):
        return int(value)
    if hasattr(value, "timestamp"):
        return int(value.timestamp() * 1000)
    return 0


def _same_text(a: str, b: str) -> bool:
    na, nb = normalize(a), normalize(b)
    if na == nb:
        return True
    return any(x + "s" == y or x + "es" == y for x, y in ((na, nb), (nb, na)))


def _core_name(name: str) -> str:
    """Nombre sin cantidad ni articulos: "2k de berenjena" -> "Berenjena"."""
    items = parse_products(name)
    return items[0].name if len(items) == 1 else name


def same_product(a: str, b: str) -> bool:
    """'Leche' == 'leches' == 'LECHE'; 'limón' == 'limones'; '2k de berenjena' == 'berenjena'."""
    return _same_text(a, b) or _same_text(_core_name(a), _core_name(b))


@dataclass
class ShoppingListRef:
    id: str
    name: str
    updated_at: int = 0


@dataclass
class ListProduct:
    id: str
    name: str
    quantity: float
    unit: str
    is_purchased: bool


@dataclass
class UserContext:
    """Datos del usuario que se cargan una sola vez por pedido."""
    categories: list[dict] = field(default_factory=list)   # {id, name, emoji}
    known: list[dict] = field(default_factory=list)        # {name, emoji, categoryId, defaultUnit}


class FirestoreVoiceStore:
    def __init__(self, db):
        self.db = db

    def user_lists(self, uid: str) -> list[ShoppingListRef]:
        docs = self.db.collection("shoppingLists").where("members", "array_contains", uid).get()
        lists = [ShoppingListRef(d.id, (d.to_dict() or {}).get("name", "Mi Lista"),
                                 _millis((d.to_dict() or {}).get("updatedAt"))) for d in docs]
        return sorted(lists, key=lambda l: -l.updated_at)

    def create_list(self, uid: str, name: str) -> ShoppingListRef:
        ref = self.db.collection("shoppingLists").document()
        ts = now_ms()
        ref.set({"name": name, "ownerId": uid, "members": [uid], "isShared": False, "emoji": "🛒",
                 "color": "", "createdAt": ts, "updatedAt": ts})
        return ShoppingListRef(ref.id, name, ts)

    def products(self, list_id: str) -> list[ListProduct]:
        docs = self.db.collection("shoppingLists").document(list_id).collection("products").get()
        out = []
        for d in docs:
            data = d.to_dict() or {}
            out.append(ListProduct(d.id, data.get("name", ""), float(data.get("quantity") or 1),
                                   data.get("unit", "Unidad"), bool(data.get("isPurchased", False))))
        return out

    def user_context(self, uid: str) -> UserContext:
        cats = [{"id": d.id, **(d.to_dict() or {})}
                for d in self.db.collection("users").document(uid).collection("categories").get()]
        known = [d.to_dict() or {} for d in self.db.collection("knownProducts").where("ownerId", "==", uid).get()]
        return UserContext(categories=cats, known=known)

    def apply(self, list_id: str, adds: list[dict], updates: list[tuple[str, dict]], deletes: list[str]):
        """Aplica todos los cambios de un pedido en un solo batch."""
        lst = self.db.collection("shoppingLists").document(list_id)
        batch = self.db.batch()
        for data in adds:
            batch.set(lst.collection("products").document(), data)
        for doc_id, fields in updates:
            batch.update(lst.collection("products").document(doc_id), fields)
        for doc_id in deletes:
            batch.delete(lst.collection("products").document(doc_id))
        batch.update(lst, {"updatedAt": now_ms()})
        batch.commit()


# --- Logica (independiente de Firestore, testeable con un store falso) -------------------

def find_list(lists: list[ShoppingListRef], name: str | None) -> ShoppingListRef | None:
    if not name:
        return None
    target = normalize(name)
    for l in lists:
        if normalize(l.name) == target:
            return l
    for l in lists:
        n = normalize(l.name)
        if target and (target in n or n in target):
            return l
    return None


def resolve_category(item: VoiceItem, ctx: UserContext, known: dict | None) -> tuple[str, str, str]:
    """(categoryId, categoryName, categoryEmoji) usando las categorias reales del usuario:
    1) la del producto conocido, 2) la sugerida por el diccionario, 3) "Otros".
    Si el usuario no tiene categorias sincronizadas se usa el nombre por defecto
    (la app la asocia por nombre o la crea al sincronizar)."""
    by_id = {c["id"]: c for c in ctx.categories}
    if known and known.get("categoryId") in by_id:
        c = by_id[known["categoryId"]]
        return c["id"], c.get("name", OTROS), c.get("emoji") or "📦"

    def by_name(name: str):
        return next((c for c in ctx.categories if normalize(c.get("name", "")) == normalize(name)), None)

    c = by_name(item.category) or by_name(OTROS)
    if c:
        return c["id"], c["name"], c.get("emoji") or DEFAULT_CATEGORY_EMOJI.get(c["name"], "📦")
    if ctx.categories:  # tiene categorias pero ninguna coincide (renombradas)
        return "", OTROS, "📦"
    return "", item.category, DEFAULT_CATEGORY_EMOJI.get(item.category, "📦")


def build_changes(items: list[VoiceItem], existing: list[ListProduct], ctx: UserContext):
    """Decide que agregar y que actualizar. Si el producto ya esta pendiente se suma la
    cantidad; si estaba tachado se vuelve a marcar como pendiente."""
    adds, updates = [], []
    added_names, already = [], []
    for item in items:
        match = next((p for p in existing if same_product(p.name, item.name)), None)
        if match and not match.is_purchased:
            if match.unit == item.unit:
                updates.append((match.id, {"quantity": match.quantity + item.quantity,
                                           "lastModifiedBy": VOICE_AUTHOR, "lastModifiedAt": now_ms()}))
                match.quantity += item.quantity
            already.append(match.name)
            continue
        if match and match.is_purchased:
            updates.append((match.id, {"isPurchased": False, "quantity": item.quantity, "unit": item.unit,
                                       "lastModifiedBy": VOICE_AUTHOR, "lastModifiedAt": now_ms()}))
            match.is_purchased = False
            added_names.append(item.name)
            continue

        known = next((k for k in ctx.known if same_product(k.get("name", ""), item.name)), None)
        unit, qty = item.unit, item.quantity
        if known and unit == "Unidad" and qty == 1 and known.get("defaultUnit"):
            unit = known["defaultUnit"]
        cat_id, cat_name, cat_emoji = resolve_category(item, ctx, known)
        adds.append({
            "name": item.name, "quantity": float(qty), "unit": unit,
            "categoryId": cat_id, "categoryName": cat_name, "categoryEmoji": cat_emoji,
            "emoji": (known or {}).get("emoji") or item.emoji, "preferredBrand": "",
            "isPurchased": False, "lastModifiedBy": VOICE_AUTHOR, "lastModifiedAt": now_ms(),
        })
        existing.append(ListProduct("", item.name, qty, unit, False))  # evita duplicar dentro del mismo pedido
        added_names.append(item.name)
    return adds, updates, added_names, already


def join_es(words: list[str]) -> str:
    words = [w.lower() for w in words]
    if len(words) <= 1:
        return "".join(words)
    return ", ".join(words[:-1]) + " y " + words[-1]
