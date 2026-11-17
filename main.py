"""Backend de precios y voz para "Uh no habia".

La logica vive en el paquete `api/`:
  api/routers/meta.py     -> /health, /app/version, /privacy, /terms
  api/routers/sepa.py     -> consultas en vivo a SEPA (/supermarkets, /prices, /search, /compare)
  api/routers/catalog.py  -> /catalog/* (catalogo Postgres + optimizador)
  api/routers/voice.py    -> /voice/alexa, /voice/google
  api/routers/auth.py     -> vinculacion de Alexa y /auth/admin-reset
  api/routers/share.py    -> compartir listas (/share/*, /invite/{id}, assetlinks)
"""
import logging
from contextlib import asynccontextmanager

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api import alexa_tokens, db, scraper
from api.config import ENABLE_INTERNAL_SCRAPER, SCRAPER_MIN_HOURS_BETWEEN_RUNS
from api.routers import auth, catalog, meta, sepa, share, voice

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
log = logging.getLogger("uhnohabia")


def _startup_scrape_if_stale():
    try:
        hours = scraper.hours_since_last_run()
    except Exception:
        log.exception("No se pudo consultar la ultima corrida del scraper")
        hours = None
    if hours is not None and hours < SCRAPER_MIN_HOURS_BETWEEN_RUNS:
        log.info("Ultimo scraping hace %.1f h; no se relanza al arrancar", hours)
        return
    scraper.run_catalog_scraper()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    sched = None
    if db.enabled():
        try:
            scraper.init_catalog_db()
        except Exception:
            log.exception("init_catalog_db fallo")
        alexa_tokens.init_store()
        if ENABLE_INTERNAL_SCRAPER:
            import threading
            threading.Thread(target=_startup_scrape_if_stale, daemon=True).start()
            sched = BackgroundScheduler(timezone="America/Argentina/Buenos_Aires")
            sched.add_job(scraper.run_catalog_scraper, "cron", hour=6, minute=0)
            sched.start()
    yield
    if sched:
        sched.shutdown(wait=False)


app = FastAPI(title="Precios API", version="2.0.0", lifespan=lifespan)

# La app Android no necesita CORS; se deja abierto solo para GET (paginas y consultas publicas).
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])

for r in (meta.router, sepa.router, catalog.router, voice.router, auth.router, share.router):
    app.include_router(r)
