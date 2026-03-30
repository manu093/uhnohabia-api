import httpx, json

r = httpx.get("https://colonial-albertine-pepin-5207cd9b.koyeb.app/compare",
    params={"products": "leche,papa,arroz", "lat": -34.6037, "lng": -58.3816, "radius_km": 10},
    timeout=60)
data = r.json()
print(json.dumps(data, indent=2, ensure_ascii=False)[:2000])
