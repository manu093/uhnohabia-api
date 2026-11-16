"""Comandos de voz (Alexa y Google) sobre las listas de Firestore.

Alexa (invocacion "mi mercado"):
  - AddProductIntent     "agregá dos leches pan y huevos [a la lista super]"
  - ReadListIntent       "qué hay en la lista" / "qué me falta"
  - RemoveProductIntent  "sacá la leche"
  - Help / Yes / No / Stop / Cancel / Fallback / NavigateHome, SessionEndedRequest
La lista elegida se recuerda durante la sesion (sessionAttributes).
"""
import logging
import os
from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import TimeoutError as FuturesTimeout
from typing import Optional

from fastapi import APIRouter, Header, HTTPException

from api import alexa_tokens
from api import firebase as fb
from api.config import GOOGLE_VOICE_KEY, secret_matches
from api.voice_parser import parse_products
from api.voice_store import (FirestoreVoiceStore, build_changes, find_list, join_es, same_product)

log = logging.getLogger(__name__)
router = APIRouter()

# Si se configura, se rechazan pedidos de otras skills (Alexa > Build > Endpoint > Skill ID)
ALEXA_SKILL_ID = os.environ.get("ALEXA_SKILL_ID", "")

DEFAULT_LIST_NAME = "Mi Lista"
# Alexa corta a los 8 s: si Firestore no responde a tiempo se avisa en vez de quedar mudo.
FIRESTORE_TIMEOUT_S = 6.5
_executor = ThreadPoolExecutor(max_workers=8)
MAX_READ = 15

HELP_TEXT = ("Podés dictarme varios productos juntos, por ejemplo: agregá dos leches, un kilo de papa y pan. "
             "También podés decir: qué hay en la lista, o sacá la leche. ¿Qué querés agregar?")


def _store() -> FirestoreVoiceStore:
    return FirestoreVoiceStore(fb.db)


# --- Acciones (compartidas por Alexa y Google) -------------------------------------------

def _pick_list(store, uid: str, requested: str | None, session_list: dict | None):
    """Devuelve (lista, aviso). Prioridad: lista pedida > lista de la sesion > la ultima usada."""
    lists = store.user_lists(uid)
    if requested:
        found = find_list(lists, requested)
        if found:
            return found, ""
        note = f"No encontré la lista {requested}. "
    else:
        note = ""
    if session_list:
        found = next((l for l in lists if l.id == session_list.get("id")), None)
        if found:
            return found, note
    if lists:
        return lists[0], note
    return store.create_list(uid, DEFAULT_LIST_NAME), note


def add_products(store, uid: str, text: str, list_name: str | None = None, session_list: dict | None = None):
    items = parse_products(text)
    if not items:
        return None, "No entendí qué producto agregar."
    lst, note = _pick_list(store, uid, list_name, session_list)
    adds, updates, added, already = build_changes(items, store.products(lst.id), store.user_context(uid))
    if adds or updates:
        store.apply(lst.id, adds, updates, [])
    parts = []
    if added:
        parts.append(f"Agregué {join_es(added)} a {lst.name}.")
    if already:
        verb = "ya estaba" if len(already) == 1 else "ya estaban"
        parts.append(f"{join_es(already).capitalize()} {verb}, le sumé la cantidad.")
    return lst, note + " ".join(parts)


def read_list(store, uid: str, list_name: str | None = None, session_list: dict | None = None):
    lst, note = _pick_list(store, uid, list_name, session_list)
    pending = [p.name for p in store.products(lst.id) if not p.is_purchased]
    if not pending:
        return lst, f"{note}La lista {lst.name} no tiene nada pendiente."
    more = f", y {len(pending) - MAX_READ} más" if len(pending) > MAX_READ else ""
    cosas = "una cosa" if len(pending) == 1 else f"{len(pending)} cosas"
    return lst, f"{note}En {lst.name} te faltan {cosas}: {join_es(pending[:MAX_READ])}{more}."


def remove_products(store, uid: str, text: str, list_name: str | None = None, session_list: dict | None = None):
    items = parse_products(text)
    if not items:
        return None, "No entendí qué producto sacar."
    lst, note = _pick_list(store, uid, list_name, session_list)
    products = store.products(lst.id)
    deletes, removed, missing = [], [], []
    for item in items:
        match = next((p for p in products if same_product(p.name, item.name) and p.id not in deletes), None)
        if match:
            deletes.append(match.id)
            removed.append(match.name)
        else:
            missing.append(item.name)
    if deletes:
        store.apply(lst.id, [], [], deletes)
    parts = []
    if removed:
        parts.append(f"Saqué {join_es(removed)} de {lst.name}.")
    if missing:
        parts.append(f"No encontré {join_es(missing)} en {lst.name}.")
    return lst, note + " ".join(parts)


# --- Alexa --------------------------------------------------------------------------------

def alexa_response(text: str, end_session: bool, reprompt: str | None = None, link_account: bool = False,
                   attributes: dict | None = None) -> dict:
    resp: dict = {"outputSpeech": {"type": "PlainText", "text": text}, "shouldEndSession": end_session}
    if reprompt and not end_session:
        resp["reprompt"] = {"outputSpeech": {"type": "PlainText", "text": reprompt}}
    if link_account:
        resp["card"] = {"type": "LinkAccount"}
    out = {"version": "1.0", "response": resp}
    if attributes:
        out["sessionAttributes"] = attributes
    return out


def _slot(intent: dict, name: str) -> str:
    return ((intent.get("slots") or {}).get(name) or {}).get("value") or ""


def _application_id(body: dict) -> str:
    return (body.get("session", {}).get("application", {}).get("applicationId")
            or body.get("context", {}).get("System", {}).get("application", {}).get("applicationId", ""))


def _access_token(body: dict) -> str:
    return (body.get("session", {}).get("user", {}).get("accessToken")
            or body.get("context", {}).get("System", {}).get("user", {}).get("accessToken", ""))


@router.post("/voice/alexa")
def process_alexa_skill(body: dict):
    if ALEXA_SKILL_ID and _application_id(body) != ALEXA_SKILL_ID:
        raise HTTPException(403, "Skill no autorizada")
    req = body.get("request", {})
    req_type = req.get("type")
    if req_type == "SessionEndedRequest":
        return {"version": "1.0", "response": {}}
    if not fb.initialized:
        return alexa_response("El servicio no está disponible en este momento. Probá más tarde.", True)

    uid = alexa_tokens.uid_for(_access_token(body))
    if not uid:
        return alexa_response("Para usar la lista, primero vinculá tu cuenta de Uh no había desde la app de Alexa.",
                              True, link_account=True)

    attrs = body.get("session", {}).get("attributes") or {}
    session_list = attrs.get("list")

    if req_type == "LaunchRequest":
        return alexa_response("¿Qué querés agregar a la lista?", False,
                              reprompt="Decime los productos, por ejemplo: leche, pan y huevos.", attributes=attrs)
    if req_type != "IntentRequest":
        return alexa_response("¿Qué querés agregar?", False, reprompt="¿Qué querés agregar?", attributes=attrs)

    intent = req.get("intent", {})
    name = intent.get("name", "")
    future = _executor.submit(_handle_intent, name, intent, uid, attrs, session_list)
    try:
        return future.result(timeout=FIRESTORE_TIMEOUT_S)
    except FuturesTimeout:
        log.error("Timeout de Firestore procesando %s de Alexa", name)
        return alexa_response("La lista está tardando en responder. Probá de nuevo en un ratito.", True)


def _handle_intent(name: str, intent: dict, uid: str, attrs: dict, session_list: dict | None) -> dict:
    store = _store()
    try:
        if name == "AddProductIntent":
            text = _slot(intent, "product")
            if not text:
                return alexa_response("¿Qué producto querés agregar?", False,
                                      reprompt="Decime qué querés agregar.", attributes=attrs)
            lst, msg = add_products(store, uid, text, _slot(intent, "listName"), session_list)
            if lst:
                attrs["list"] = {"id": lst.id, "name": lst.name}
                return alexa_response(f"{msg} ¿Algo más?", False, reprompt="¿Querés agregar algo más?",
                                      attributes=attrs)
            return alexa_response(f"{msg} Probá de nuevo, por ejemplo: agregá leche.", False,
                                  reprompt="¿Qué querés agregar?", attributes=attrs)

        if name == "ReadListIntent":
            lst, msg = read_list(store, uid, _slot(intent, "listName"), session_list)
            attrs["list"] = {"id": lst.id, "name": lst.name}
            return alexa_response(f"{msg} ¿Querés agregar algo?", False, reprompt="¿Querés agregar algo?",
                                  attributes=attrs)

        if name == "RemoveProductIntent":
            lst, msg = remove_products(store, uid, _slot(intent, "product"), _slot(intent, "listName"), session_list)
            if lst:
                attrs["list"] = {"id": lst.id, "name": lst.name}
            return alexa_response(f"{msg} ¿Algo más?", False, reprompt="¿Querés hacer algo más?", attributes=attrs)

        if name == "AMAZON.HelpIntent":
            return alexa_response(HELP_TEXT, False, reprompt="¿Qué querés agregar?", attributes=attrs)
        if name == "AMAZON.YesIntent":
            return alexa_response("Dale, decime.", False, reprompt="¿Qué querés agregar?", attributes=attrs)
        if name in ("AMAZON.NoIntent", "AMAZON.StopIntent", "AMAZON.CancelIntent", "AMAZON.NavigateHomeIntent"):
            return alexa_response("Listo. ¡Buenas compras!", True)
        if name == "AMAZON.FallbackIntent":
            return alexa_response("No te entendí. Podés decir, por ejemplo: agregá leche y pan.", False,
                                  reprompt="¿Qué querés agregar?", attributes=attrs)
    except Exception:
        log.exception("Error procesando %s de Alexa", name)
        return alexa_response("Hubo un problema con la lista. Probá de nuevo en un ratito.", True)

    return alexa_response("No te entendí. ¿Qué querés agregar?", False, reprompt="¿Qué querés agregar?",
                          attributes=attrs)


# --- Google (Dialogflow webhook) ----------------------------------------------------------

@router.post("/voice/google")
def process_google_assistant(body: dict, x_voice_key: Optional[str] = Header(None)):
    """Webhook de Dialogflow. Requiere el header X-Voice-Key == GOOGLE_VOICE_KEY
    (Dialogflow > Fulfillment > Headers). Sin la variable, queda deshabilitado."""
    if not GOOGLE_VOICE_KEY:
        raise HTTPException(503, "Endpoint deshabilitado")
    if not secret_matches(x_voice_key, GOOGLE_VOICE_KEY):
        raise HTTPException(401, "Unauthorized")
    if not fb.initialized:
        return {"fulfillmentText": "Servicio de voz no disponible."}
    params = body.get("queryResult", {}).get("parameters", {})
    uid = body.get("originalDetectIntentRequest", {}).get("payload", {}).get("user", {}).get("userId")
    if not uid:
        return {"fulfillmentText": "Necesitás vincular tu cuenta para usar esta función."}
    text = params.get("product", "")
    if not text:
        return {"fulfillmentText": "No se pudo interpretar el comando de voz."}
    try:
        _lst, msg = add_products(_store(), str(uid), str(text), params.get("list"))
    except Exception:
        log.exception("Error procesando comando de Google")
        return {"fulfillmentText": "Hubo un problema agregando el producto."}
    return {"fulfillmentText": msg}
