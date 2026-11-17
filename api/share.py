"""Compartir listas: invitaciones por email y por link.

Todo pasa por el backend (Firebase Admin) para no depender de las reglas de Firestore:
el invitado todavia no es miembro de la lista, asi que desde la app no podria leerla ni
agregarse a `members`. La identidad se verifica con el ID token de Firebase del usuario.
"""
import time
from dataclasses import dataclass

LINK_TTL_DAYS = 30


def now_ms() -> int:
    return int(time.time() * 1000)


class ShareError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


@dataclass
class User:
    uid: str
    email: str
    name: str


@dataclass
class ListInfo:
    id: str
    name: str
    members: list[str]


class FirestoreShareStore:
    def __init__(self, db):
        self.db = db

    def get_list(self, list_id: str) -> ListInfo | None:
        doc = self.db.collection("shoppingLists").document(list_id).get()
        if not doc.exists:
            return None
        d = doc.to_dict() or {}
        return ListInfo(doc.id, d.get("name", "Lista"), list(d.get("members") or []))

    def create_invitation(self, data: dict) -> str:
        ref = self.db.collection("invitations").document()
        ref.set(data)
        return ref.id

    def get_invitation(self, inv_id: str) -> dict | None:
        doc = self.db.collection("invitations").document(inv_id).get()
        return {"id": doc.id, **(doc.to_dict() or {})} if doc.exists else None

    def pending_for_email(self, email: str) -> list[dict]:
        """Invitaciones pendientes para el email, sin distinguir mayusculas (las versiones viejas
        de la app guardaban el email tal cual se tipeaba). Son pocas: se filtran aca."""
        out = []
        for doc in self.db.collection("invitations").where("status", "==", "pending").get():
            d = doc.to_dict() or {}
            if (d.get("inviteeEmail") or "").strip().lower() == email:
                out.append({"id": doc.id, **d})
        return out

    def add_member(self, list_id: str, uid: str):
        from firebase_admin import firestore
        self.db.collection("shoppingLists").document(list_id).update(
            {"members": firestore.ArrayUnion([uid]), "isShared": True, "updatedAt": now_ms()})

    def update_invitation(self, inv_id: str, fields: dict):
        self.db.collection("invitations").document(inv_id).update(fields)

    def delete_invitation(self, inv_id: str):
        self.db.collection("invitations").document(inv_id).delete()

    def notify(self, uid: str, message: str):
        """Mismo formato que usa la app (coleccion notifications, se muestra al abrirla)."""
        self.db.collection("notifications").document().set(
            {"userId": uid, "message": message, "type": "member_joined", "read": False, "createdAt": now_ms()})


# --- Logica ----------------------------------------------------------------------------

def _require_member(store, user: User, list_id: str) -> ListInfo:
    lst = store.get_list(list_id)
    if not lst:
        raise ShareError(404, "La lista no existe.")
    if user.uid not in lst.members:
        raise ShareError(403, "No sos miembro de esta lista.")
    return lst


def invite_by_email(store, user: User, list_id: str, email: str) -> dict:
    email = (email or "").strip().lower()
    if "@" not in email or "." not in email.split("@")[-1]:
        raise ShareError(400, "El email no es válido.")
    if email == user.email:
        raise ShareError(400, "No te podés invitar a vos mismo.")
    lst = _require_member(store, user, list_id)
    store.create_invitation({
        "type": "email", "listId": lst.id, "listName": lst.name, "inviteeEmail": email,
        "invitedBy": user.name, "invitedByUid": user.uid, "status": "pending", "createdAt": now_ms(),
    })
    return {"ok": True, "message": f"Invitación enviada a {email}. La va a ver al abrir la app."}


def create_link(store, user: User, list_id: str, base_url: str) -> dict:
    lst = _require_member(store, user, list_id)
    inv_id = store.create_invitation({
        "type": "link", "listId": lst.id, "listName": lst.name, "invitedBy": user.name,
        "invitedByUid": user.uid, "status": "active", "createdAt": now_ms(),
        "expiresAt": now_ms() + LINK_TTL_DAYS * 86_400_000,
    })
    return {"url": f"{base_url.rstrip('/')}/invite/{inv_id}", "id": inv_id, "listName": lst.name}


def pending_invitations(store, user: User) -> list[dict]:
    invs = store.pending_for_email(user.email)
    out = []
    for inv in sorted(invs, key=lambda i: -(i.get("createdAt") or 0)):
        name = inv.get("listName")
        if not name:  # invitaciones viejas no guardaban el nombre
            lst = store.get_list(inv.get("listId", ""))
            if not lst:
                continue
            name = lst.name
        out.append({"id": inv["id"], "listId": inv.get("listId"), "listName": name,
                    "inviteeEmail": inv.get("inviteeEmail", user.email), "invitedBy": inv.get("invitedBy", "")})
    return out


def describe_invitation(store, inv_id: str) -> dict | None:
    """Datos publicos de un link de invitacion (para la pagina web)."""
    inv = store.get_invitation(inv_id)
    if not inv:
        return None
    valid = _link_valid(inv) if inv.get("type") == "link" else inv.get("status") == "pending"
    return {"listName": inv.get("listName") or "una lista", "invitedBy": inv.get("invitedBy") or "", "valid": valid}


def _link_valid(inv: dict) -> bool:
    return inv.get("status") == "active" and (inv.get("expiresAt") or 0) > now_ms()


def accept(store, user: User, inv_id: str) -> dict:
    inv = store.get_invitation(inv_id)
    if not inv:
        raise ShareError(404, "La invitación no existe o ya no es válida.")
    is_link = inv.get("type") == "link"
    if is_link:
        if not _link_valid(inv):
            raise ShareError(410, "Este link de invitación venció. Pedí uno nuevo.")
    else:
        if inv.get("status") != "pending":
            raise ShareError(410, "Esta invitación ya fue usada.")
        if (inv.get("inviteeEmail") or "").strip().lower() != user.email:
            raise ShareError(403, "Esta invitación es para otro email.")
    lst = store.get_list(inv.get("listId", ""))
    if not lst:
        raise ShareError(404, "La lista ya no existe.")
    already = user.uid in lst.members
    if not already:
        store.add_member(lst.id, user.uid)
    if not is_link:
        store.update_invitation(inv_id, {"status": "accepted", "acceptedBy": user.uid, "acceptedAt": now_ms()})
    if not already and inv.get("invitedByUid") and inv.get("invitedByUid") != user.uid:
        try:
            store.notify(inv["invitedByUid"], f"{user.name} se unió a tu lista {lst.name}")
        except Exception:
            pass
    return {"listId": lst.id, "listName": lst.name, "alreadyMember": already}


def decline(store, user: User, inv_id: str) -> dict:
    inv = store.get_invitation(inv_id)
    if not inv:
        return {"ok": True}
    if inv.get("type") == "link":
        raise ShareError(400, "Los links no se rechazan, simplemente no los abras.")
    if (inv.get("inviteeEmail") or "").strip().lower() != user.email:
        raise ShareError(403, "Esta invitación es para otro email.")
    store.delete_invitation(inv_id)
    return {"ok": True}
