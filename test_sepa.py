import httpx
r = httpx.get(
    "https://d3e6htiiul5ek9.cloudfront.net/prod/sucursales",
    params={"lat": -34.6037, "lng": -58.3816},
    headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"},
    timeout=15
)
d = r.json()
print(f"Status: {r.status_code}, Sucursales: {len(d.get('sucursales', []))}")
