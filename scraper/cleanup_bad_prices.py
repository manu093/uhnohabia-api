"""One-time cleanup: remove Jumbo/Disco products with price < $500 (price-per-unit artifacts)."""
import psycopg2
cn = psycopg2.connect("${DATABASE_URL}")
cr = cn.cursor()
cr.execute("DELETE FROM vtex_productos WHERE cadena IN ('Jumbo','Disco') AND precio < 500")
print(f"Removed {cr.rowcount} bad Jumbo/Disco prices (< $500)")
cn.commit()
cr.execute("SELECT cadena, COUNT(*), MIN(precio)::int, AVG(precio)::int FROM vtex_productos GROUP BY cadena ORDER BY cadena")
for r in cr.fetchall():
    print(f"  {r[0]}: {r[1]} products, min ${r[2]}, avg ${r[3]}")
cr.execute("SELECT COUNT(*) FROM vtex_productos")
print(f"\nTotal: {cr.fetchone()[0]} products")
cr.close(); cn.close()
