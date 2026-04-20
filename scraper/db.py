"""
Database module - Postgres (Neon) connection and schema.
"""
import os
import psycopg2
from psycopg2.extras import execute_values

DATABASE_URL = os.environ.get("DATABASE_URL", "")


def get_conn():
    # Neon pooler adds channel_binding=require, psycopg2 handles it via the URL
    return psycopg2.connect(DATABASE_URL)


def init_db():
    """Create tables if they don't exist."""
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
    CREATE TABLE IF NOT EXISTS zonas (
        id SERIAL PRIMARY KEY,
        nombre TEXT UNIQUE NOT NULL,
        lat DOUBLE PRECISION NOT NULL,
        lng DOUBLE PRECISION NOT NULL,
        radio_km DOUBLE PRECISION NOT NULL DEFAULT 10.0
    );
    """)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS sucursales (
        id TEXT PRIMARY KEY,
        cadena TEXT NOT NULL,
        nombre TEXT,
        direccion TEXT,
        localidad TEXT,
        provincia TEXT,
        lat TEXT,
        lng TEXT,
        zona_id INTEGER REFERENCES zonas(id),
        updated_at TIMESTAMP DEFAULT NOW()
    );
    """)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS productos (
        id TEXT PRIMARY KEY,
        nombre TEXT NOT NULL,
        marca TEXT,
        presentacion TEXT,
        nombre_lower TEXT,
        marca_lower TEXT,
        updated_at TIMESTAMP DEFAULT NOW()
    );
    """)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS precios (
        id TEXT PRIMARY KEY,
        producto_id TEXT REFERENCES productos(id) ON DELETE CASCADE,
        sucursal_id TEXT REFERENCES sucursales(id) ON DELETE CASCADE,
        producto_nombre TEXT,
        cadena TEXT,
        precio DOUBLE PRECISION NOT NULL,
        fecha TEXT,
        updated_at TIMESTAMP DEFAULT NOW()
    );
    """)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS scraper_status (
        id SERIAL PRIMARY KEY,
        zona_id INTEGER REFERENCES zonas(id),
        timestamp TIMESTAMP DEFAULT NOW(),
        duration_seconds DOUBLE PRECISION,
        products_count INTEGER,
        prices_count INTEGER
    );
    """)
    # Indexes
    cur.execute("CREATE INDEX IF NOT EXISTS idx_productos_nombre_lower ON productos(nombre_lower);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_productos_marca_lower ON productos(marca_lower);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_precios_producto_id ON precios(producto_id);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_precios_sucursal_id ON precios(sucursal_id);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_precios_cadena ON precios(cadena);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_sucursales_zona ON sucursales(zona_id);")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_sucursales_localidad ON sucursales(localidad);")
    conn.commit()
    cur.close()
    conn.close()


def seed_zonas():
    """Insert default zones if not present."""
    conn = get_conn()
    cur = conn.cursor()
    # Zona Sur GBA: centro en Burzaco, radio 10km
    # Cubre: Glew, Longchamps, Burzaco, Adrogué, Temperley, Lomas, Llavallol, Turdera
    cur.execute("""
    INSERT INTO zonas (nombre, lat, lng, radio_km)
    VALUES ('Zona Sur GBA', -34.83, -58.39, 12.0)
    ON CONFLICT (nombre) DO NOTHING;
    """)
    conn.commit()
    cur.close()
    conn.close()


def upsert_sucursales(rows):
    """Bulk upsert sucursales."""
    if not rows:
        return
    conn = get_conn()
    cur = conn.cursor()
    execute_values(cur, """
    INSERT INTO sucursales (id, cadena, nombre, direccion, localidad, provincia, lat, lng, zona_id)
    VALUES %s
    ON CONFLICT (id) DO UPDATE SET
        cadena=EXCLUDED.cadena, nombre=EXCLUDED.nombre, direccion=EXCLUDED.direccion,
        localidad=EXCLUDED.localidad, provincia=EXCLUDED.provincia,
        lat=EXCLUDED.lat, lng=EXCLUDED.lng, zona_id=EXCLUDED.zona_id, updated_at=NOW();
    """, rows)
    conn.commit()
    cur.close()
    conn.close()


def upsert_productos(rows):
    """Bulk upsert productos."""
    if not rows:
        return
    conn = get_conn()
    cur = conn.cursor()
    execute_values(cur, """
    INSERT INTO productos (id, nombre, marca, presentacion, nombre_lower, marca_lower)
    VALUES %s
    ON CONFLICT (id) DO UPDATE SET
        nombre=EXCLUDED.nombre, marca=EXCLUDED.marca, presentacion=EXCLUDED.presentacion,
        nombre_lower=EXCLUDED.nombre_lower, marca_lower=EXCLUDED.marca_lower, updated_at=NOW();
    """, rows)
    conn.commit()
    cur.close()
    conn.close()


def upsert_precios(rows):
    """Bulk upsert precios."""
    if not rows:
        return
    conn = get_conn()
    cur = conn.cursor()
    execute_values(cur, """
    INSERT INTO precios (id, producto_id, sucursal_id, producto_nombre, cadena, precio, fecha)
    VALUES %s
    ON CONFLICT (id) DO UPDATE SET
        precio=EXCLUDED.precio, fecha=EXCLUDED.fecha, cadena=EXCLUDED.cadena, updated_at=NOW();
    """, rows)
    conn.commit()
    cur.close()
    conn.close()


def save_scraper_status(zona_id, duration, products_count, prices_count):
    conn = get_conn()
    cur = conn.cursor()
    cur.execute("""
    INSERT INTO scraper_status (zona_id, duration_seconds, products_count, prices_count)
    VALUES (%s, %s, %s, %s);
    """, (zona_id, duration, products_count, prices_count))
    conn.commit()
    cur.close()
    conn.close()
