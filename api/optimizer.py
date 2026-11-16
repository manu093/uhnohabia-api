"""Optimizador de compra: elige la cadena mas barata y reparte el pago entre medios
con descuento. Logica pura (sin DB ni HTTP) para poder testearla; el acceso a datos
se inyecta con un objeto `PriceLookup`."""
import math
import re
from typing import Protocol

WEIGHT_UNITS = ("kg", "kilo", "kilos", "g", "gr", "l", "lt", "litro", "litros", "ml", "cc")

_WEIGHT_RE = re.compile(r"(\d+(?:[.,]\d+)?)\s*(kg|kgm|g|gr|grs|ml|cc|l|lt|lts|litro|litros)\b")


def extract_weight(product_name: str) -> tuple[float | None, str | None]:
    """'Arroz 1 kg' -> (1000.0, 'g'); 'Leche 500 ml' -> (500.0, 'ml')."""
    m = _WEIGHT_RE.search(product_name.lower())
    if not m:
        return None, None
    val = float(m.group(1).replace(",", "."))
    unit = m.group(2)
    if unit in ("kg", "kgm"):
        return val * 1000, "g"
    if unit in ("g", "gr", "grs"):
        return val, "g"
    if unit in ("l", "lt", "lts", "litro", "litros"):
        return val * 1000, "ml"
    return val, "ml"  # ml, cc


def units_needed(product_name: str, qty: float, unit: str | None) -> float:
    """Cuantos envases comprar para cubrir `qty` `unit` (ej: 2 kg con paquetes de 500 g -> 4)."""
    if not unit:
        return qty
    u = unit.lower().strip()
    weight, _ = extract_weight(product_name)
    if not weight:
        return qty
    if u in ("kg", "kilo", "kilos", "l", "lt", "lts", "litro", "litros"):
        needed = qty * 1000
    elif u in ("g", "gr", "grs", "gramos", "ml", "cc"):
        needed = qty
    else:
        return qty  # "Unidad", etc.
    return max(1, math.ceil(needed / weight))


def promo_applies(promo_dia: str | None, dia: str | None) -> bool:
    if not promo_dia or not dia:
        return True
    return promo_dia.lower() == dia.lower()


def card_ok(tarjeta: str | None, medio_id, tarjetas_sel: dict) -> bool:
    """Si la promo es de una tarjeta especifica y el usuario eligio tarjetas para ese
    medio de pago, solo aplica si coincide."""
    if not tarjeta:
        return True
    sel = tarjetas_sel.get(str(medio_id))
    if not sel:
        return True
    return tarjeta in sel


def apply_discounts(total: float, promos: list[tuple]) -> list[dict]:
    """Reparte `total` entre promos (banco, tarjeta, pct, dia, tope) de mayor a menor
    descuento, respetando topes de reintegro. Lo que queda va a 'Efectivo'."""
    remaining = total
    pagos = []
    for banco, tarjeta, pct, _dia, tope in sorted(promos, key=lambda p: -p[2]):
        if remaining <= 0:
            break
        if pct <= 0:
            continue
        descuento = remaining * (pct / 100)
        tope_val = tope or 0
        tope_aplicado = tope_val > 0 and descuento >= tope_val
        if tope_val > 0 and descuento > tope_val:
            descuento = tope_val
            monto = tope_val / (pct / 100)
        else:
            monto = remaining
        pagos.append({"medioPago": banco, "tarjeta": tarjeta, "monto": round(monto, 2),
                      "descuentoPct": pct, "ahorro": round(descuento, 2), "topeAplicado": tope_aplicado})
        remaining -= monto
    if remaining > 0.005:
        pagos.append({"medioPago": "Efectivo", "tarjeta": None, "monto": round(remaining, 2),
                      "descuentoPct": 0, "ahorro": 0, "topeAplicado": False})
    return pagos


class PriceLookup(Protocol):
    def by_id(self, product_id: str) -> tuple | None:
        """(nombre, marca, precio, cadena) del producto con ese id."""
    def same_product(self, cadena: str, nombre: str, marca: str) -> tuple | None:
        """(nombre, marca, precio) del mismo producto en otra cadena."""
    def candidates(self, cadena: str, nombre: str) -> list[tuple]:
        """Hasta 10 (nombre, marca, precio) que matchean la busqueda en la cadena."""
    def realtime(self, cadena: str, nombre: str) -> tuple | None:
        """(nombre, marca, precio) buscando en vivo en la web de la cadena."""


def _pick_for_chain(cadena: str, prod: dict, lookup: PriceLookup) -> dict | None:
    pid = prod.get("producto_id")
    nombre = prod.get("nombre", "")
    qty = prod.get("cantidad", 1) or 1
    unidad = prod.get("unidad", "") or ""

    def chosen(row, units):
        return {"nombre": row[0], "marca": row[1], "precio": row[2], "cantidad": units, "busqueda": nombre}

    if pid and pid != "cualquier_marca":
        row = lookup.by_id(pid)
        if not row:
            return None
        if row[3] != cadena:
            # El producto elegido es de otra cadena: buscar el mismo producto en esta.
            same = lookup.same_product(cadena, row[0], row[1])
            if not same:
                return None
            row = same
        return chosen(row, units_needed(row[0], qty, unidad))

    # cualquier_marca: el mas barato de la cadena (por peso si la unidad es de peso)
    rows = lookup.candidates(cadena, nombre) if nombre.strip() else []
    if rows:
        if unidad.lower().strip() in WEIGHT_UNITS:
            best = min(rows, key=lambda r: r[2] * units_needed(r[0], qty, unidad))
            return chosen(best, units_needed(best[0], qty, unidad))
        return chosen(rows[0], qty)
    rt = lookup.realtime(cadena, nombre) if nombre.strip() else None
    if rt:
        return chosen(rt, units_needed(rt[0], qty, unidad))
    return None


def optimize(productos: list[dict], cadenas: list[str], promos: list[tuple], lookup: PriceLookup,
             dia: str = "", tarjetas_sel: dict | None = None, cadenas_filter: list[str] | None = None) -> dict:
    """promos: filas (cadena, banco, tarjeta, pct, dia_semana, tope, medio_pago_id)."""
    tarjetas_sel = tarjetas_sel or {}
    if cadenas_filter:
        cadenas = [c for c in cadenas if c in cadenas_filter]

    ranking = []
    for cadena in cadenas:
        total = 0.0
        missing, selected = [], []
        for prod in productos:
            pick = _pick_for_chain(cadena, prod, lookup)
            if pick:
                total += pick["precio"] * pick["cantidad"]
                selected.append(pick)
            else:
                missing.append(prod.get("nombre") or prod.get("producto_id", ""))
        if total <= 0:
            continue

        cadena_promos = [(p[1], p[2], p[3], p[4], p[5]) for p in promos
                         if p[0] == cadena and promo_applies(p[4], dia) and card_ok(p[2], p[6], tarjetas_sel)]
        pagos = apply_discounts(total, cadena_promos)
        ahorro = sum(p["ahorro"] for p in pagos)
        ranking.append({
            "cadena": cadena, "totalOriginal": round(total, 2), "totalFinal": round(total - ahorro, 2),
            "ahorro": round(ahorro, 2), "ahorroPorcentaje": round(ahorro / total * 100, 2),
            "distribucionPagos": pagos, "productosFaltantes": missing, "productosSeleccionados": selected,
        })

    # Orden por precio final (la app avisa aparte si a la recomendada le faltan productos).
    ranking.sort(key=lambda r: r["totalFinal"])
    best = ranking[0] if ranking else None
    return {
        "cadenaRecomendada": best["cadena"] if best else None,
        "totalOriginal": best["totalOriginal"] if best else 0,
        "totalFinal": best["totalFinal"] if best else 0,
        "ahorroTotal": best["ahorro"] if best else 0,
        "ahorroPorcentaje": best["ahorroPorcentaje"] if best else 0,
        "distribucionPagos": best["distribucionPagos"] if best else [],
        "productosFaltantes": best["productosFaltantes"] if best else [],
        "productosSeleccionados": best["productosSeleccionados"] if best else [],
        "rankingCadenas": ranking,
    }
