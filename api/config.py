"""Configuracion centralizada leida de variables de entorno.

Los secretos NO tienen valor por defecto: si falta la variable, el endpoint
que la necesita queda deshabilitado (falla cerrado) en lugar de aceptar una
clave conocida publicamente.
"""
import hmac
import json
import os
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent

DATABASE_URL = os.environ.get("DATABASE_URL", "")

# Secretos (sin default a proposito)
ADMIN_KEY = os.environ.get("ADMIN_KEY", "")
SCRAPER_KEY = os.environ.get("SCRAPER_KEY", "")
GOOGLE_VOICE_KEY = os.environ.get("GOOGLE_VOICE_KEY", "")

# La Web API key de Firebase no es secreta (viaja dentro del APK en google-services.json).
FIREBASE_API_KEY = os.environ.get("FIREBASE_API_KEY", "AIzaSyA0ZD7YqjNV0Uu7cFg35wFQ8kKQRtFTHkw")

# Scraper interno del backend. Ponelo en "false" si el scraper de la carpeta scraper/
# (load_full_catalog.py con cron) ya corre en otro lado, para no scrapear dos veces.
ENABLE_INTERNAL_SCRAPER = os.environ.get("ENABLE_INTERNAL_SCRAPER", "true").lower() != "false"
# No relanzar el scraper al arrancar si la ultima corrida fue hace menos de estas horas.
SCRAPER_MIN_HOURS_BETWEEN_RUNS = float(os.environ.get("SCRAPER_MIN_HOURS_BETWEEN_RUNS", "20"))

GITHUB_RELEASES = "https://github.com/manu093/uhnohabia-api/releases/download"


def secret_matches(provided: str | None, expected: str) -> bool:
    """Compara en tiempo constante. Si el secreto esperado no esta configurado, siempre False."""
    if not expected or not provided:
        return False
    return hmac.compare_digest(provided.encode(), expected.encode())


def load_version() -> dict:
    """Version de la app: unica fuente de verdad compartida con app/build.gradle.kts."""
    data = json.loads((ROOT_DIR / "version.json").read_text(encoding="utf-8"))
    name = data["versionName"]
    apk_url = f"{GITHUB_RELEASES}/v{name}/UhNoHabia.apk"
    return {
        "versionCode": data["versionCode"],
        "versionName": name,
        "apkUrl": apk_url,
        "releaseNotes": data.get("releaseNotes", ""),
        "forceUpdate": data.get("forceUpdate", False),
    }
