"""Flujo completo de Alexa contra un Firestore falso en memoria."""
import pytest
from fastapi.testclient import TestClient

import main
from api import alexa_tokens
from api import firebase as fb
from api.routers import voice
from api.voice_store import ListProduct, ShoppingListRef, UserContext


class FakeStore:
    def __init__(self):
        self.lists = [ShoppingListRef("l1", "Super", 200), ShoppingListRef("l2", "Farmacia", 100)]
        self.items = {"l1": [], "l2": []}
        self.ctx = UserContext(
            categories=[{"id": "c-granja", "name": "Granja", "emoji": "🥚"},
                        {"id": "c-otros", "name": "Otros", "emoji": "📦"},
                        {"id": "c-verdu", "name": "Verdulería", "emoji": "🥬"}],
            known=[{"name": "Papa", "emoji": "🥔", "categoryId": "c-verdu", "defaultUnit": "Kg"}],
        )
        self.writes = []
        self._n = 0

    def user_lists(self, uid):
        return sorted(self.lists, key=lambda l: -l.updated_at)

    def create_list(self, uid, name):
        ref = ShoppingListRef("new", name, 0)
        self.lists.append(ref)
        self.items["new"] = []
        return ref

    def products(self, list_id):
        return [ListProduct(p["id"], p["name"], p["quantity"], p["unit"], p["isPurchased"])
                for p in self.items[list_id]]

    def user_context(self, uid):
        return self.ctx

    def apply(self, list_id, adds, updates, deletes):
        self.writes.append((list_id, adds, updates, deletes))
        for data in adds:
            self._n += 1
            self.items[list_id].append({"id": f"p{self._n}", **data})
        for doc_id, fields in updates:
            next(p for p in self.items[list_id] if p["id"] == doc_id).update(fields)
        self.items[list_id] = [p for p in self.items[list_id] if p["id"] not in deletes]


@pytest.fixture
def store(monkeypatch):
    s = FakeStore()
    monkeypatch.setattr(voice, "_store", lambda: s)
    monkeypatch.setattr(fb, "initialized", True)
    monkeypatch.setattr(alexa_tokens, "uid_for", lambda token: "uid-1" if token == "tok" else "")
    return s


@pytest.fixture
def client():
    with TestClient(main.app) as c:
        yield c


def alexa(client, intent=None, slots=None, req_type="IntentRequest", token="tok", attrs=None):
    request = {"type": req_type}
    if intent:
        request["intent"] = {"name": intent, "slots": {k: {"name": k, "value": v} for k, v in (slots or {}).items()}}
    body = {"version": "1.0", "session": {"user": {"accessToken": token}, "attributes": attrs or {}},
            "request": request}
    r = client.post("/voice/alexa", json=body)
    assert r.status_code == 200
    return r.json()


def speech(resp):
    return resp["response"]["outputSpeech"]["text"]


def test_add_several_products_with_categories(client, store):
    resp = alexa(client, "AddProductIntent", {"product": "dos leches un kilo de tomate y pan"})
    assert speech(resp).startswith("Agregué leches, tomate y pan a Super.")
    assert resp["response"]["shouldEndSession"] is False
    assert "reprompt" in resp["response"]
    assert resp["sessionAttributes"]["list"] == {"id": "l1", "name": "Super"}
    by_name = {p["name"]: p for p in store.items["l1"]}
    assert by_name["Leches"]["quantity"] == 2 and by_name["Leches"]["categoryId"] == "c-granja"
    assert by_name["Leches"]["emoji"] == "🥛"
    assert by_name["Tomate"]["unit"] == "Kg" and by_name["Tomate"]["categoryName"] == "Verdulería"
    # "Pan" -> Almacén no existe en las categorias del usuario -> Otros
    assert by_name["Pan"]["categoryId"] == "c-otros"
    # formato que espera la app: millis enteros, autor, no comprado
    assert isinstance(by_name["Pan"]["lastModifiedAt"], int)
    assert by_name["Pan"]["isPurchased"] is False and by_name["Pan"]["lastModifiedBy"] == "Alexa"
    assert len(store.writes) == 1  # un solo batch


def test_known_product_uses_user_history(client, store):
    alexa(client, "AddProductIntent", {"product": "papas"})
    [p] = store.items["l1"]
    assert (p["unit"], p["emoji"], p["categoryId"]) == ("Kg", "🥔", "c-verdu")


def test_duplicate_sums_quantity_and_purchased_is_reactivated(client, store):
    store.items["l1"] = [
        {"id": "a", "name": "Leche", "quantity": 1.0, "unit": "Unidad", "isPurchased": False},
        {"id": "b", "name": "Pan", "quantity": 1.0, "unit": "Unidad", "isPurchased": True},
    ]
    resp = alexa(client, "AddProductIntent", {"product": "leche y pan"})
    items = {p["id"]: p for p in store.items["l1"]}
    assert items["a"]["quantity"] == 2.0
    assert items["b"]["isPurchased"] is False
    assert len(store.items["l1"]) == 2
    assert "Leche ya estaba" in speech(resp)


def test_list_by_name_and_session_memory(client, store):
    resp = alexa(client, "AddProductIntent", {"product": "ibuprofeno", "listName": "farmacia"})
    assert "a Farmacia" in speech(resp)
    # siguiente pedido sin nombre de lista: sigue en Farmacia por la sesion
    resp2 = alexa(client, "AddProductIntent", {"product": "algodón"}, attrs=resp["sessionAttributes"])
    assert "a Farmacia" in speech(resp2)
    assert [p["name"] for p in store.items["l2"]] == ["Ibuprofeno", "Algodón"]


def test_unknown_list_falls_back_with_notice(client, store):
    resp = alexa(client, "AddProductIntent", {"product": "leche", "listName": "ferretería"})
    assert speech(resp).startswith("No encontré la lista ferretería. Agregué leche a Super.")


def test_creates_list_if_user_has_none(client, store):
    store.lists = []
    resp = alexa(client, "AddProductIntent", {"product": "leche"})
    assert "a Mi Lista" in speech(resp)


def test_read_list(client, store):
    store.items["l1"] = [
        {"id": "a", "name": "Leche", "quantity": 1.0, "unit": "Unidad", "isPurchased": False},
        {"id": "b", "name": "Pan", "quantity": 1.0, "unit": "Unidad", "isPurchased": True},
        {"id": "c", "name": "Yerba", "quantity": 1.0, "unit": "Unidad", "isPurchased": False},
    ]
    resp = alexa(client, "ReadListIntent")
    assert speech(resp).startswith("En Super te faltan 2 cosas: leche y yerba.")


def test_remove_product(client, store):
    store.items["l1"] = [{"id": "a", "name": "Leche", "quantity": 1.0, "unit": "Unidad", "isPurchased": False}]
    resp = alexa(client, "RemoveProductIntent", {"product": "la leche y el pan"})
    assert store.items["l1"] == []
    assert "Saqué leche de Super." in speech(resp) and "No encontré pan" in speech(resp)


@pytest.mark.parametrize("intent", ["AMAZON.StopIntent", "AMAZON.CancelIntent", "AMAZON.NoIntent"])
def test_stop_ends_session(client, store, intent):
    resp = alexa(client, intent)
    assert resp["response"]["shouldEndSession"] is True


def test_session_ended_returns_empty_response(client, store):
    resp = alexa(client, req_type="SessionEndedRequest")
    assert resp == {"version": "1.0", "response": {}}


def test_launch_and_help_keep_session_open(client, store):
    for resp in (alexa(client, req_type="LaunchRequest"), alexa(client, "AMAZON.HelpIntent"),
                 alexa(client, "AMAZON.FallbackIntent")):
        assert resp["response"]["shouldEndSession"] is False and "reprompt" in resp["response"]


def test_missing_product_asks_again(client, store):
    resp = alexa(client, "AddProductIntent", {})
    assert resp["response"]["shouldEndSession"] is False
    assert store.writes == []


def test_unlinked_account_asks_to_link(client, store):
    resp = alexa(client, "AddProductIntent", {"product": "leche"}, token="")
    assert resp["response"]["card"] == {"type": "LinkAccount"}
    assert store.writes == []


def test_skill_id_check(client, store, monkeypatch):
    monkeypatch.setattr(voice, "ALEXA_SKILL_ID", "amzn1.ask.skill.mia")
    body = {"session": {"application": {"applicationId": "amzn1.ask.skill.otra"}},
            "request": {"type": "LaunchRequest"}}
    assert client.post("/voice/alexa", json=body).status_code == 403


def test_firestore_hang_answers_before_alexa_timeout(client, store, monkeypatch):
    import time as _t
    monkeypatch.setattr(voice, "FIRESTORE_TIMEOUT_S", 0.3)
    monkeypatch.setattr(store, "user_lists", lambda uid: _t.sleep(2) or [])
    start = _t.monotonic()
    resp = alexa(client, "ReadListIntent")
    assert _t.monotonic() - start < 1.5
    assert "tardando" in speech(resp) and resp["response"]["shouldEndSession"] is True
