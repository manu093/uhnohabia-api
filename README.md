# Uh no había

App Android de listas de compras compartidas con comparador de precios para Argentina, más su backend.

| Carpeta | Qué es |
|---|---|
| `app/` | App Android (Kotlin, Compose, Room, Firebase, Hilt) |
| `main.py` + `api/` | Backend FastAPI (catálogo de precios, optimizador, voz, vinculación de Alexa) |
| `scraper/` | Scraper standalone del catálogo completo (cron 6 y 20 h) |
| `alexa/`, `assistant/` | Modelos de interacción de Alexa / Google Assistant |
| `version.json` | **Versión única** de la app: la lee Gradle y el endpoint `/app/version` |

## Backend

```bash
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt   # en Linux/mac: .venv/bin/pip
.venv/Scripts/python -m pytest
.venv/Scripts/uvicorn main:app --reload
```

### Variables de entorno

| Variable | Obligatoria | Uso |
|---|---|---|
| `DATABASE_URL` | sí (catálogo) | Postgres (Neon) |
| `FIREBASE_SERVICE_ACCOUNT` | sí (voz/Alexa) | JSON de la service account |
| `SCRAPER_KEY` | para `/catalog/scrape` e `/catalog/import_products` | Se envía en el header `X-Api-Key`. Sin la variable, esos endpoints devuelven 503 |
| `ADMIN_KEY` | para `/auth/admin-reset` | Header `X-Admin-Key`. Sin la variable, el endpoint está deshabilitado |
| `GOOGLE_VOICE_KEY` | para `/voice/google` | Header `X-Voice-Key` configurado en Dialogflow. Sin la variable, deshabilitado |
| `ENABLE_INTERNAL_SCRAPER` | no (`true`) | Poné `false` si ya corre el cron de `scraper/` |
| `SCRAPER_MIN_HOURS_BETWEEN_RUNS` | no (`20`) | No re-scrapear al reiniciar si la última corrida es reciente |

> Los secretos **no tienen valor por defecto**: usá valores largos y aleatorios
> (`python -c "import secrets; print(secrets.token_urlsafe(32))"`).

## Android

Requiere `app/google-services.json` y, para release, `keystore.properties` (ninguno se versiona).
Para compilar usá JDK 17 o 21. La URL del backend se puede cambiar con `-PapiBaseUrl=https://...`.

Para publicar una versión nueva, editá solo `version.json` (versionCode, versionName, releaseNotes).
