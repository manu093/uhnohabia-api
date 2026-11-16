"""Fuentes de precios externas: SEPA (Precios Claros) y tiendas VTEX."""
import logging

import requests

log = logging.getLogger(__name__)

SEPA_BASE_URL = "https://d3e6htiiul5ek9.cloudfront.net/prod"
SEPA_HEADERS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}

# Tiendas VTEX que se scrapean periodicamente
VTEX_SCRAPE_STORES = {
    "DIA": "https://diaonline.supermercadosdia.com.ar",
    "Jumbo": "https://www.jumbo.com.ar",
    "Disco": "https://www.disco.com.ar",
    "Carrefour": "https://www.carrefour.com.ar",
}

# Tiendas VTEX consultadas en tiempo real cuando la DB tiene pocos resultados
VTEX_REALTIME_STORES = {**VTEX_SCRAPE_STORES, "Changomas": "https://www.masonline.com.ar"}

# Precio minimo valido por cadena (Jumbo/Disco a veces devuelven precio por unidad de medida)
_MIN_PRICE = {"Jumbo": 1000, "Disco": 1000}
DEFAULT_MIN_PRICE = 100


def min_price_for(cadena: str) -> float:
    return _MIN_PRICE.get(cadena, DEFAULT_MIN_PRICE)


def vtex_search(base_url: str, query: str, _from: int = 0, _to: int = 49, timeout: float = 15) -> list:
    """Busca productos en la API publica de un sitio VTEX. Devuelve [] ante cualquier error."""
    try:
        r = requests.get(
            f"{base_url}/api/catalog_system/pub/products/search/{query}",
            params={"_from": _from, "_to": _to},
            headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"},
            timeout=timeout,
        )
        if r.status_code in (200, 206):
            data = r.json()
            return data if isinstance(data, list) else []
    except (requests.RequestException, ValueError) as e:
        log.debug("VTEX %s '%s' fallo: %s", base_url, query, e)
    return []


def vtex_offer(p: dict) -> tuple[float, int, str] | None:
    """Extrae (precio, stock, imagen) del primer item/seller de un producto VTEX."""
    try:
        item = p["items"][0]
        offer = item["sellers"][0]["commertialOffer"]
        price = float(offer.get("Price", 0))
        stock = int(offer.get("AvailableQuantity", 0))
        images = item.get("images", [])
        image = images[0]["imageUrl"] if images else ""
        return price, stock, image
    except (KeyError, IndexError, TypeError, ValueError):
        return None


def sepa_get(path: str, params: dict, timeout: float = 10) -> dict | None:
    try:
        r = requests.get(f"{SEPA_BASE_URL}{path}", params=params, headers=SEPA_HEADERS, timeout=timeout)
        if r.status_code in (200, 206):
            return r.json()
    except (requests.RequestException, ValueError) as e:
        log.debug("SEPA %s fallo: %s", path, e)
    return None
