"""Inicializacion opcional de Firebase Admin (solo la usan voz y Alexa)."""
import json
import logging
import os

import firebase_admin
from firebase_admin import credentials, firestore

log = logging.getLogger(__name__)

db = None
initialized = False

try:
    if not firebase_admin._apps:
        if os.path.exists("serviceAccountKey.json"):
            firebase_admin.initialize_app(credentials.Certificate("serviceAccountKey.json"))
        elif os.environ.get("FIREBASE_SERVICE_ACCOUNT"):
            info = json.loads(os.environ["FIREBASE_SERVICE_ACCOUNT"])
            # Si al pegar el JSON los saltos de linea quedaron doblemente escapados (una barra
            # y una "n" literales), la firma del token es invalida: se corrigen aca.
            if "\\n" in info.get("private_key", ""):
                info["private_key"] = info["private_key"].replace("\\n", "\n")
            firebase_admin.initialize_app(credentials.Certificate(info))
        else:
            firebase_admin.initialize_app()
    db = firestore.client()
    initialized = True
except Exception as e:  # sin credenciales en local/tests
    log.warning("Firebase init skipped: %s. Voice endpoints will not work.", e)
