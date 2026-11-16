"""Vinculacion de cuenta de Alexa (flujo OAuth implicito) y reset de contrasena de admin."""
import html
import json
import logging
from typing import Optional
from urllib.parse import quote, urlparse

import httpx
from fastapi import APIRouter, Form, Header, HTTPException
from fastapi.responses import HTMLResponse
from firebase_admin import auth as firebase_auth
from pydantic import BaseModel

from api import alexa_tokens
from api import firebase as fb
from api.config import ADMIN_KEY, FIREBASE_API_KEY, secret_matches

log = logging.getLogger(__name__)
router = APIRouter()

_PAGE_STYLE = ("body{font-family:sans-serif;max-width:400px;margin:40px auto;padding:20px}"
               "input{width:100%;padding:12px;margin:8px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px}"
               "button{width:100%;padding:14px;background:#4CAF50;color:white;border:none;border-radius:4px;"
               "font-size:16px;cursor:pointer}h2{text-align:center}")


def _error_page(msg: str, status: int = 200) -> HTMLResponse:
    return HTMLResponse(f"<html><head><meta charset=utf-8><style>{_PAGE_STYLE}</style></head><body>"
                        f"<h2>{html.escape(msg)}</h2><a href='javascript:history.back()'>Volver</a></body></html>",
                        status_code=status)


def _is_amazon_redirect(uri: str) -> bool:
    """Solo se permite volver a hosts de Amazon por https (evita open redirect)."""
    p = urlparse(uri)
    host = (p.hostname or "").lower()
    amazon = host in ("amazon.com", "amazon.co.jp") or host.endswith(".amazon.com") or host.endswith(".amazon.co.jp")
    return p.scheme == "https" and amazon


@router.get("/auth/login", response_class=HTMLResponse)
async def auth_login_page(redirect_uri: str = "", state: str = "", client_id: str = "", response_type: str = ""):
    """Pagina de login que muestra Alexa durante la vinculacion de cuenta."""
    if not _is_amazon_redirect(redirect_uri):
        return _error_page("Error: redirect_uri no permitido", 400)
    return f"""<html><head><meta charset=utf-8><meta name="viewport" content="width=device-width, initial-scale=1">
    <style>{_PAGE_STYLE}</style></head>
    <body><h2>🛒 Uh no había</h2><p>Iniciá sesión para vincular tu cuenta con Alexa</p>
    <form method="post" action="/auth/token">
    <input type="hidden" name="redirect_uri" value="{html.escape(redirect_uri, quote=True)}">
    <input type="hidden" name="state" value="{html.escape(state, quote=True)}">
    <input type="email" name="email" placeholder="Correo electrónico" required>
    <input type="password" name="password" placeholder="Contraseña" required>
    <button type="submit">Vincular cuenta</button>
    </form></body></html>"""


@router.post("/auth/token")
async def auth_token(redirect_uri: str = Form(""), state: str = Form(""),
                     email: str = Form(""), password: str = Form("")):
    """Verifica email/contrasena contra Firebase y vuelve a Alexa con un access token."""
    if not _is_amazon_redirect(redirect_uri):
        return _error_page("Error: redirect_uri no permitido", 400)
    if not email or not password:
        return _error_page("Completá email y contraseña", 400)

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.post(
                f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}",
                json={"email": email, "password": password, "returnSecureToken": True},
            )
    except httpx.HTTPError:
        log.exception("Error contactando Firebase Auth")
        return _error_page("No se pudo verificar las credenciales. Probá de nuevo más tarde.", 502)

    if r.status_code != 200:
        # No se devuelve el detalle de Firebase (EMAIL_NOT_FOUND vs INVALID_PASSWORD) para
        # no revelar que emails estan registrados.
        return _error_page("Email o contraseña incorrectos", 401)

    uid = r.json().get("localId")
    if not uid:
        return _error_page("No se pudo verificar las credenciales", 502)

    try:
        token = alexa_tokens.issue(uid)
    except Exception:
        log.exception("No se pudo guardar el token de Alexa")
        return _error_page("Error interno. Probá de nuevo más tarde.", 500)

    fragment = f"state={quote(state)}&access_token={token}&token_type=Bearer"
    redirect_url = f"{redirect_uri}#{fragment}"
    return HTMLResponse(f"<html><body><script>window.location.replace({json.dumps(redirect_url)});"
                        f"</script></body></html>")


class AdminResetBody(BaseModel):
    email: str
    newPassword: str
    admin_key: Optional[str] = None  # compatibilidad: preferir el header X-Admin-Key


@router.post("/auth/admin-reset")
def admin_reset_password(body: AdminResetBody, x_admin_key: Optional[str] = Header(None)):
    """Resetea la contrasena de un usuario. Requiere ADMIN_KEY configurada en el servidor;
    sin esa variable el endpoint queda deshabilitado."""
    if not ADMIN_KEY:
        raise HTTPException(503, "Endpoint deshabilitado")
    if not secret_matches(x_admin_key or body.admin_key, ADMIN_KEY):
        raise HTTPException(401, "Unauthorized")
    if not fb.initialized:
        raise HTTPException(503, "Firebase not initialized")
    if len(body.newPassword) < 8:
        raise HTTPException(400, "La contraseña debe tener al menos 8 caracteres")
    try:
        user = firebase_auth.get_user_by_email(body.email)
        firebase_auth.update_user(user.uid, password=body.newPassword)
    except firebase_auth.UserNotFoundError:
        raise HTTPException(404, "Usuario no encontrado")
    log.warning("Password reseteada por admin para uid=%s", user.uid)
    return {"ok": True}
