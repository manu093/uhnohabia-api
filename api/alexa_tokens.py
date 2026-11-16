"""Tokens de vinculacion de Alexa.

En la DB se guarda solo el SHA-256 del token: si la base se filtra, los tokens no
sirven para escribir en las listas de nadie. Los tokens viejos (guardados en texto
plano, 32 caracteres hex) se migran a hash la primera vez que arranca el servidor.
"""
import hashlib
import logging
import secrets

from api import db

log = logging.getLogger(__name__)

_cache: dict[str, str] = {}  # hash -> uid


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def init_store():
    if not db.enabled():
        return
    try:
        with db.cursor(commit=True) as cur:
            cur.execute("CREATE TABLE IF NOT EXISTS alexa_tokens (token TEXT PRIMARY KEY, uid TEXT NOT NULL, "
                        "created_at TIMESTAMP DEFAULT NOW())")
            cur.execute("SELECT token, uid FROM alexa_tokens")
            for token, uid in cur.fetchall():
                if len(token) == 32:  # formato viejo: uuid4().hex en texto plano
                    hashed = _hash(token)
                    cur.execute("INSERT INTO alexa_tokens (token, uid) VALUES (%s, %s) ON CONFLICT (token) DO NOTHING",
                                (hashed, uid))
                    cur.execute("DELETE FROM alexa_tokens WHERE token=%s", (token,))
                    _cache[hashed] = uid
                else:
                    _cache[token] = uid
    except Exception:
        log.exception("No se pudo inicializar alexa_tokens")


def issue(uid: str) -> str:
    token = secrets.token_hex(32)
    hashed = _hash(token)
    _cache[hashed] = uid
    if db.enabled():
        with db.cursor(commit=True) as cur:
            cur.execute("INSERT INTO alexa_tokens (token, uid) VALUES (%s, %s) "
                        "ON CONFLICT (token) DO UPDATE SET uid=EXCLUDED.uid", (hashed, uid))
    return token


def uid_for(token: str) -> str:
    if not token:
        return ""
    hashed = _hash(token)
    if hashed in _cache:
        return _cache[hashed]
    if db.enabled():  # otra instancia pudo haberlo emitido
        try:
            with db.cursor() as cur:
                cur.execute("SELECT uid FROM alexa_tokens WHERE token=%s", (hashed,))
                row = cur.fetchone()
            if row:
                _cache[hashed] = row[0]
                return row[0]
        except Exception:
            log.exception("Error buscando token de Alexa")
    return ""
