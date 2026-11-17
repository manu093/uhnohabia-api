"""Endpoints para compartir listas (requieren el ID token de Firebase del usuario) y la
pagina publica /invite/{id} que abre la app."""
import html
import logging
import os
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel

from api import firebase as fb
from api import share
from api.share import FirestoreShareStore, ShareError, User

log = logging.getLogger(__name__)
router = APIRouter()

PUBLIC_BASE_URL = os.environ.get("PUBLIC_BASE_URL", "https://colonial-albertine-pepin-5207cd9b.koyeb.app")
APP_PACKAGE = "com.sharedshoppinglists.app"
# Huella SHA-256 del certificado de firma del APK (Android App Links)
APP_CERT_SHA256 = os.environ.get(
    "APP_CERT_SHA256",
    "62:A0:C3:BF:FB:4A:25:EF:C8:55:BC:5E:A7:ED:2B:A4:0C:6B:F6:82:21:BE:35:A0:F9:6F:BA:8A:63:C4:0D:B0")


def _store() -> FirestoreShareStore:
    return FirestoreShareStore(fb.db)


def _verify_token(token: str) -> dict:
    from firebase_admin import auth
    return auth.verify_id_token(token)


def current_user(authorization: Optional[str] = Header(None)) -> User:
    if not fb.initialized:
        raise HTTPException(503, "Servicio no disponible")
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "Falta el token de sesión")
    try:
        claims = _verify_token(authorization.split(" ", 1)[1].strip())
    except Exception:
        raise HTTPException(401, "Sesión inválida. Volvé a iniciar sesión.")
    email = (claims.get("email") or "").strip().lower()
    name = claims.get("name") or (email.split("@")[0] if email else "Alguien")
    return User(uid=claims["uid"], email=email, name=name)


def _run(fn, *args):
    try:
        return fn(*args)
    except ShareError as e:
        raise HTTPException(e.status, e.message)


class InviteBody(BaseModel):
    email: str


@router.post("/share/lists/{list_id}/invite")
def invite(list_id: str, body: InviteBody, user: User = Depends(current_user)):
    return _run(share.invite_by_email, _store(), user, list_id, body.email)


@router.post("/share/lists/{list_id}/link")
def create_link(list_id: str, user: User = Depends(current_user)):
    return _run(share.create_link, _store(), user, list_id, PUBLIC_BASE_URL)


@router.get("/share/invitations")
def pending(user: User = Depends(current_user)):
    return _run(share.pending_invitations, _store(), user)


@router.post("/share/invitations/{inv_id}/accept")
def accept(inv_id: str, user: User = Depends(current_user)):
    return _run(share.accept, _store(), user, inv_id)


@router.post("/share/invitations/{inv_id}/decline")
def decline(inv_id: str, user: User = Depends(current_user)):
    return _run(share.decline, _store(), user, inv_id)


# --- Link publico ------------------------------------------------------------------------

@router.get("/.well-known/assetlinks.json")
def assetlinks():
    """Permite que Android abra los links /invite/... directo en la app (App Links)."""
    return JSONResponse([{
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {"namespace": "android_app", "package_name": APP_PACKAGE,
                   "sha256_cert_fingerprints": [APP_CERT_SHA256]},
    }])


@router.get("/invite/{inv_id}", response_class=HTMLResponse)
def invite_page(inv_id: str):
    """Se ve solo si el link se abre en el navegador (app no instalada o link no verificado)."""
    info = None
    if fb.initialized:
        try:
            info = share.describe_invitation(_store(), inv_id)
        except Exception:
            log.exception("No se pudo leer la invitacion %s", inv_id)
    safe_id = html.escape(inv_id, quote=True)
    if info and info["valid"]:
        who = f"{html.escape(info['invitedBy'])} te invitó a" if info["invitedBy"] else "Te invitaron a"
        title = f"{who} la lista <b>{html.escape(info['listName'])}</b>"
    elif info:
        title = "Este link de invitación venció. Pedile uno nuevo a quien te lo mandó."
    else:
        title = "Invitación a una lista de Uh no había"
    open_app = (f"intent://invite/{safe_id}#Intent;scheme=uhnohabia;package={APP_PACKAGE};"
                f"S.browser_fallback_url={html.escape(PUBLIC_BASE_URL + '/app/download', quote=True)};end")
    return f"""<!doctype html><html lang=es><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>Uh no había - Invitación</title>
<style>body{{font-family:system-ui,sans-serif;max-width:420px;margin:48px auto;padding:0 20px;text-align:center;
color:#222;background:#FFFBF5}}h1{{font-size:22px;font-weight:600;line-height:1.4}}
a.b{{display:block;margin:14px 0;padding:14px;border-radius:12px;text-decoration:none;font-size:17px}}
.p{{background:#E65100;color:#fff}}.s{{background:#FFF3E0;color:#BF360C}}p{{color:#666;font-size:14px}}</style></head>
<body><div style="font-size:56px">🛒</div><h1>{title}</h1>
<a class="b p" href="{open_app}">Abrir en la app</a>
<a class="b s" href="{html.escape(PUBLIC_BASE_URL, quote=True)}/app/download">Descargar la app</a>
<p>Si no tenés la app, descargala, iniciá sesión y volvé a tocar el link.</p></body></html>"""
