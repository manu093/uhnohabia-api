"""Comandos de voz (Alexa y Google) que agregan productos a las listas en Firestore."""
import logging
import re
from typing import Optional

from fastapi import APIRouter, Header, HTTPException
from firebase_admin import firestore
from pydantic import BaseModel

from api import alexa_tokens
from api import firebase as fb
from api.config import GOOGLE_VOICE_KEY, secret_matches

log = logging.getLogger(__name__)
router = APIRouter()

PRODUCT_CATALOG = [
    "leche entera", "leche descremada", "pan blanco", "pan integral",
    "arroz blanco", "arroz integral", "queso cremoso", "queso rallado",
    "yogur natural", "yogur frutilla", "manzana", "banana", "tomate",
    "aceite de girasol", "aceite de oliva", "azúcar", "sal fina",
    "harina", "fideos", "manteca", "huevos", "pollo", "carne picada",
    "agua mineral", "gaseosa", "cerveza", "vino",
]


def find_matching_products(name: str) -> list[str]:
    n = name.lower().strip()
    exact = [p for p in PRODUCT_CATALOG if p == n]
    if exact:
        return exact
    prefix = [p for p in PRODUCT_CATALOG if p.startswith(n)]
    if prefix:
        return prefix
    return [p for p in PRODUCT_CATALOG if n in p]


def parse_multiple_products(raw: str) -> list[str]:
    return [s.strip() for s in re.split(r",|\by\b|\band\b", raw, flags=re.IGNORECASE) if s.strip()]


class VoiceCommand(BaseModel):
    userId: str
    action: str = "ADD_PRODUCT"
    productName: str
    listName: Optional[str] = None
    quantity: Optional[int] = None
    unit: Optional[str] = None


def _to_int(value) -> Optional[int]:
    try:
        return int(float(value)) if value not in (None, "", "?") else None
    except (TypeError, ValueError):
        return None


@router.post("/voice/google")
def process_google_assistant(body: dict, x_voice_key: Optional[str] = Header(None)):
    """Webhook de Dialogflow. Requiere el header X-Voice-Key == GOOGLE_VOICE_KEY
    (configuralo en Dialogflow > Fulfillment > Headers). Sin la variable, queda deshabilitado."""
    if not GOOGLE_VOICE_KEY:
        raise HTTPException(503, "Endpoint deshabilitado")
    if not secret_matches(x_voice_key, GOOGLE_VOICE_KEY):
        raise HTTPException(401, "Unauthorized")
    if not fb.initialized:
        return {"fulfillmentText": "Servicio de voz no disponible."}
    params = body.get("queryResult", {}).get("parameters", {})
    user_id = body.get("originalDetectIntentRequest", {}).get("payload", {}).get("user", {}).get("userId")
    if not user_id:
        return {"fulfillmentText": "Necesitás vincular tu cuenta para usar esta función."}
    product_name = params.get("product", "")
    if not product_name:
        return {"fulfillmentText": "No se pudo interpretar el comando de voz."}

    cmd = VoiceCommand(userId=str(user_id), productName=product_name, listName=params.get("list"),
                       quantity=_to_int(params.get("quantity")), unit=params.get("unit"))
    result = process_voice_command(cmd)
    if result["type"] == "disambiguation":
        return {"fulfillmentText": f"¿Cuál producto querés agregar? {', '.join(result['suggestions'])}"}
    return {"fulfillmentText": result["message"]}


def alexa_response(text: str, end_session: bool, link_account: bool = False) -> dict:
    resp = {"outputSpeech": {"type": "PlainText", "text": text}, "shouldEndSession": end_session}
    if link_account:
        resp["card"] = {"type": "LinkAccount"}
    return {"version": "1.0", "response": resp}


@router.post("/voice/alexa")
def process_alexa_skill(body: dict):
    if not fb.initialized:
        return alexa_response("Servicio de voz no disponible.", True)
    req = body.get("request", {})
    if req.get("type") != "IntentRequest":  # LaunchRequest ("abrí super") u otros
        return alexa_response("¿Qué producto querés agregar?", False)

    token = body.get("session", {}).get("user", {}).get("accessToken", "")
    user_id = alexa_tokens.uid_for(token)
    if not user_id:
        return alexa_response("Primero vinculá tu cuenta de Uh no había desde la app de Alexa.", True,
                              link_account=True)

    slots = req.get("intent", {}).get("slots", {})
    product_name = slots.get("product", {}).get("value", "")
    if not product_name:
        return alexa_response("No entendí. ¿Qué producto querés agregar?", False)

    cmd = VoiceCommand(userId=user_id, productName=product_name,
                       listName=slots.get("list", {}).get("value"),
                       quantity=_to_int(slots.get("quantity", {}).get("value")),
                       unit=slots.get("unit", {}).get("value"))
    try:
        result = process_voice_command(cmd)
    except Exception:
        log.exception("Error procesando comando de Alexa")
        return alexa_response("Hubo un problema agregando el producto. Probá de nuevo.", True)
    return alexa_response(result["message"] + " ¿Querés agregar algo más?", False)


def process_voice_command(cmd: VoiceCommand) -> dict:
    if not cmd.productName.strip():
        return {"type": "error", "message": "No se especificó un producto."}

    list_name = (cmd.listName or "").strip()
    product_names = parse_multiple_products(cmd.productName)

    # Find existing list: if no name specified, use the first list of the user
    lists_ref = fb.db.collection("shoppingLists")
    if list_name:
        query = lists_ref.where("ownerId", "==", cmd.userId).where("name", "==", list_name).limit(1).get()
    else:
        query = lists_ref.where("ownerId", "==", cmd.userId).limit(1).get()

    if not query:
        actual_name = list_name or "Mi Lista"
        new_ref = lists_ref.document()
        new_ref.set({"name": actual_name, "ownerId": cmd.userId, "members": [cmd.userId],
                     "isShared": False, "createdAt": firestore.SERVER_TIMESTAMP, "updatedAt": firestore.SERVER_TIMESTAMP})
        list_id = new_ref.id
    else:
        list_id = query[0].id
        actual_name = query[0].to_dict().get("name", "Mi Lista")

    # Search for existing product data: first in knownProducts collection, then in user's lists
    def find_existing_product(name):
        try:
            # Try exact match first, then capitalized
            for search_name in [name, name.capitalize(), name.title(), name.lower()]:
                known = fb.db.collection("knownProducts").where("ownerId", "==", cmd.userId).where("name", "==", search_name).limit(1).get()
                if known:
                    return known[0].to_dict()
            # Fallback: search in user's existing lists
            user_lists = lists_ref.where("ownerId", "==", cmd.userId).get()
            for lst in user_lists:
                for search_name in [name, name.capitalize(), name.title()]:
                    products = fb.db.collection("shoppingLists").document(lst.id).collection("products").where("name", "==", search_name).limit(1).get()
                    if products:
                        return products[0].to_dict()
        except Exception:
            pass
        return {}

    added = []
    for raw_name in product_names:
        name = raw_name.strip().capitalize()
        existing = find_existing_product(name)
        product_data = {
            "name": name, "quantity": cmd.quantity or 1,
            "unit": existing.get("unit", cmd.unit or "Unidad"),
            "categoryId": existing.get("categoryId", ""),
            "categoryName": existing.get("categoryName", "Otros"),
            "categoryEmoji": existing.get("categoryEmoji", "📦"),
            "emoji": existing.get("emoji", ""),
            "isPurchased": False, "lastModifiedBy": cmd.userId,
            "lastModifiedAt": firestore.SERVER_TIMESTAMP
        }
        fb.db.collection("shoppingLists").document(list_id).collection("products").document().set(product_data)
        added.append(name)

    fb.db.collection("shoppingLists").document(list_id).update({"updatedAt": firestore.SERVER_TIMESTAMP})

    if len(added) == 1:
        return {"type": "success", "message": f"Listo, agregué {added[0]} a la lista {actual_name}."}
    return {"type": "success", "message": f"Listo, agregué {len(added)} productos a la lista {actual_name}."}
