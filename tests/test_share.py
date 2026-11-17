"""Compartir listas: invitaciones por email y por link, contra un store falso."""
import pytest
from fastapi.testclient import TestClient

import main
from api import firebase as fb
from api import share
from api.routers import share as share_router
from api.share import ListInfo

USERS = {
    "tok-ana": {"uid": "ana", "email": "ana@gmail.com", "name": "Ana"},
    "tok-juan": {"uid": "juan", "email": "Juan.Perez@Gmail.com", "name": "Juan"},
    "tok-eve": {"uid": "eve", "email": "eve@gmail.com", "name": "Eve"},
}


class FakeStore:
    def __init__(self):
        self.lists = {"l1": ListInfo("l1", "Super", ["ana"])}
        self.invs = {}
        self.notifications = []
        self._n = 0

    def get_list(self, list_id):
        lst = self.lists.get(list_id)
        return ListInfo(lst.id, lst.name, list(lst.members)) if lst else None

    def create_invitation(self, data):
        self._n += 1
        inv_id = f"inv{self._n}"
        self.invs[inv_id] = dict(data)
        return inv_id

    def get_invitation(self, inv_id):
        d = self.invs.get(inv_id)
        return {"id": inv_id, **d} if d else None

    def pending_for_email(self, email):
        return [{"id": k, **v} for k, v in self.invs.items()
                if v.get("status") == "pending" and (v.get("inviteeEmail") or "").strip().lower() == email]

    def add_member(self, list_id, uid):
        if uid not in self.lists[list_id].members:
            self.lists[list_id].members.append(uid)

    def update_invitation(self, inv_id, fields):
        self.invs[inv_id].update(fields)

    def delete_invitation(self, inv_id):
        self.invs.pop(inv_id, None)

    def notify(self, uid, message):
        self.notifications.append((uid, message))


@pytest.fixture
def store(monkeypatch):
    s = FakeStore()
    monkeypatch.setattr(share_router, "_store", lambda: s)
    monkeypatch.setattr(fb, "initialized", True)

    def verify(token):
        if token not in USERS:
            raise ValueError("bad token")
        return USERS[token]
    monkeypatch.setattr(share_router, "_verify_token", verify)
    return s


@pytest.fixture
def client():
    with TestClient(main.app) as c:
        yield c


def auth(tok):
    return {"Authorization": f"Bearer {tok}"}


def test_requires_valid_token(client, store):
    assert client.get("/share/invitations").status_code == 401
    assert client.get("/share/invitations", headers=auth("nope")).status_code == 401


def test_email_invite_is_case_insensitive_and_accept_adds_member(client, store):
    r = client.post("/share/lists/l1/invite", json={"email": "  JUAN.perez@gmail.com "}, headers=auth("tok-ana"))
    assert r.status_code == 200
    pend = client.get("/share/invitations", headers=auth("tok-juan")).json()
    assert [(p["listName"], p["invitedBy"]) for p in pend] == [("Super", "Ana")]
    r = client.post(f"/share/invitations/{pend[0]['id']}/accept", headers=auth("tok-juan"))
    assert r.json() == {"listId": "l1", "listName": "Super", "alreadyMember": False}
    assert store.lists["l1"].members == ["ana", "juan"]
    assert client.get("/share/invitations", headers=auth("tok-juan")).json() == []
    assert store.notifications == [("ana", "Juan se unió a tu lista Super")]


def test_legacy_invitation_with_typed_email_and_no_list_name(client, store):
    store.invs["old"] = {"listId": "l1", "inviteeEmail": "Juan.Perez@Gmail.com ", "status": "pending"}
    pend = client.get("/share/invitations", headers=auth("tok-juan")).json()
    assert pend[0]["id"] == "old" and pend[0]["listName"] == "Super"


def test_email_invite_cannot_be_used_by_someone_else(client, store):
    client.post("/share/lists/l1/invite", json={"email": "juan.perez@gmail.com"}, headers=auth("tok-ana"))
    r = client.post("/share/invitations/inv1/accept", headers=auth("tok-eve"))
    assert r.status_code == 403
    assert "eve" not in store.lists["l1"].members


def test_only_members_can_share(client, store):
    r = client.post("/share/lists/l1/invite", json={"email": "x@y.com"}, headers=auth("tok-eve"))
    assert r.status_code == 403
    assert client.post("/share/lists/l1/link", headers=auth("tok-eve")).status_code == 403
    assert client.post("/share/lists/nope/link", headers=auth("tok-ana")).status_code == 404


def test_invalid_or_own_email(client, store):
    assert client.post("/share/lists/l1/invite", json={"email": "juan"}, headers=auth("tok-ana")).status_code == 400
    assert client.post("/share/lists/l1/invite", json={"email": "ANA@gmail.com"},
                       headers=auth("tok-ana")).status_code == 400


def test_link_can_be_used_by_several_people(client, store):
    r = client.post("/share/lists/l1/link", headers=auth("tok-ana")).json()
    assert r["url"].endswith("/invite/inv1")
    for tok, uid in (("tok-juan", "juan"), ("tok-eve", "eve")):
        assert client.post("/share/invitations/inv1/accept", headers=auth(tok)).status_code == 200
    assert store.lists["l1"].members == ["ana", "juan", "eve"]
    # aceptar de nuevo no duplica
    r2 = client.post("/share/invitations/inv1/accept", headers=auth("tok-juan")).json()
    assert r2["alreadyMember"] is True and store.lists["l1"].members.count("juan") == 1


def test_expired_link(client, store, monkeypatch):
    client.post("/share/lists/l1/link", headers=auth("tok-ana"))
    store.invs["inv1"]["expiresAt"] = share.now_ms() - 1
    r = client.post("/share/invitations/inv1/accept", headers=auth("tok-juan"))
    assert r.status_code == 410 and "venció" in r.json()["detail"]


def test_decline(client, store):
    client.post("/share/lists/l1/invite", json={"email": "juan.perez@gmail.com"}, headers=auth("tok-ana"))
    assert client.post("/share/invitations/inv1/decline", headers=auth("tok-eve")).status_code == 403
    assert client.post("/share/invitations/inv1/decline", headers=auth("tok-juan")).status_code == 200
    assert "inv1" not in store.invs


def test_invite_page_and_assetlinks(client, store):
    client.post("/share/lists/l1/link", headers=auth("tok-ana"))
    page = client.get("/invite/inv1").text
    assert "Ana te invitó a" in page and "<b>Super</b>" in page
    assert "intent://invite/inv1#Intent;scheme=uhnohabia;package=com.sharedshoppinglists.app" in page
    assert "Invitación a una lista" in client.get("/invite/desconocida").text
    links = client.get("/.well-known/assetlinks.json").json()
    assert links[0]["target"]["package_name"] == "com.sharedshoppinglists.app"
    assert links[0]["target"]["sha256_cert_fingerprints"][0].startswith("62:A0:C3")


def test_invite_page_escapes_html(client, store):
    store.lists["l1"].name = "<script>x</script>"
    client.post("/share/lists/l1/link", headers=auth("tok-ana"))
    assert "<script>x</script>" not in client.get("/invite/inv1").text
