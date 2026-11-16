"""Pool de conexiones a Postgres (antes se abria una conexion nueva por request)."""
import logging
import threading
from contextlib import contextmanager

import psycopg2
from psycopg2.pool import ThreadedConnectionPool

from api.config import DATABASE_URL

log = logging.getLogger(__name__)

_pool: ThreadedConnectionPool | None = None
_pool_lock = threading.Lock()


def enabled() -> bool:
    return bool(DATABASE_URL)


def _get_pool() -> ThreadedConnectionPool:
    global _pool
    if _pool is None:
        with _pool_lock:
            if _pool is None:
                _pool = ThreadedConnectionPool(1, 10, DATABASE_URL)
    return _pool


@contextmanager
def cursor(commit: bool = False):
    """Entrega un cursor; hace commit si commit=True y rollback ante errores.

    Si la conexion del pool esta rota (Neon corta conexiones ociosas) se descarta.
    """
    pool = _get_pool()
    conn = pool.getconn()
    broken = False
    try:
        cur = conn.cursor()
        try:
            yield cur
            if commit:
                conn.commit()
            else:
                conn.rollback()  # cierra la transaccion implicita de los SELECT
        finally:
            cur.close()
    except (psycopg2.OperationalError, psycopg2.InterfaceError):
        broken = True
        raise
    except Exception:
        try:
            conn.rollback()
        except Exception:
            broken = True
        raise
    finally:
        pool.putconn(conn, close=broken or bool(conn.closed))
