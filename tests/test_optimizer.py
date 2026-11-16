import pytest

from api.optimizer import apply_discounts, card_ok, extract_weight, optimize, promo_applies, units_needed


@pytest.mark.parametrize("name,expected", [
    ("Arroz Gallo 1 kg", (1000.0, "g")),
    ("Yerba 500g", (500.0, "g")),
    ("Leche 1,5 L", (1500.0, "ml")),
    ("Gaseosa 2.25 lts", (2250.0, "ml")),
    ("Shampoo 400 cc", (400.0, "ml")),
    ("Huevos x12", (None, None)),
])
def test_extract_weight(name, expected):
    assert extract_weight(name) == expected


def test_units_needed():
    assert units_needed("Arroz 500 g", 2, "kg") == 4
    assert units_needed("Arroz 1 kg", 1.5, "kg") == 2      # redondea para arriba
    assert units_needed("Arroz 1 kg", 100, "g") == 1       # minimo 1
    assert units_needed("Leche 1 L", 3, "litros") == 3
    assert units_needed("Huevos x12", 2, "kg") == 2        # sin peso: usa la cantidad
    assert units_needed("Arroz 1 kg", 3, "Unidad") == 3
    assert units_needed("Arroz 1 kg", 3, None) == 3


def test_promo_and_card_filters():
    assert promo_applies(None, "Lunes")
    assert promo_applies("Lunes", "")
    assert promo_applies("lunes", "Lunes")
    assert not promo_applies("Martes", "Lunes")
    assert card_ok(None, 1, {"1": ["Visa"]})
    assert card_ok("Visa", 1, {})
    assert card_ok("Visa", 1, {"1": ["Visa"]})
    assert not card_ok("Amex", 1, {"1": ["Visa"]})


def test_apply_discounts_without_cap_covers_everything():
    pagos = apply_discounts(10000, [("BBVA", "Visa", 15, None, None)])
    assert pagos == [{"medioPago": "BBVA", "tarjeta": "Visa", "monto": 10000, "descuentoPct": 15,
                      "ahorro": 1500, "topeAplicado": False}]


def test_apply_discounts_respects_cap_and_chains_promos():
    # 20% con tope 1000 -> cubre 5000; el resto (5000) al 10% sin tope
    pagos = apply_discounts(10000, [("B", None, 10, None, None), ("A", None, 20, None, 1000)])
    assert [p["medioPago"] for p in pagos] == ["A", "B"]
    assert pagos[0]["monto"] == 5000 and pagos[0]["ahorro"] == 1000 and pagos[0]["topeAplicado"]
    assert pagos[1]["monto"] == 5000 and pagos[1]["ahorro"] == 500
    assert sum(p["monto"] for p in pagos) == 10000


def test_apply_discounts_remainder_goes_to_cash():
    pagos = apply_discounts(10000, [("A", None, 20, None, 1000)])
    assert pagos[-1]["medioPago"] == "Efectivo" and pagos[-1]["monto"] == 5000


class FakeLookup:
    def __init__(self, products):
        # products: id -> (nombre, marca, precio, cadena)
        self.products = products

    def by_id(self, pid):
        return self.products.get(pid)

    def same_product(self, cadena, nombre, marca):
        for n, m, precio, c in self.products.values():
            if c == cadena and n.lower() == nombre.lower() and m.lower() == marca.lower():
                return (n, m, precio)
        return None

    def candidates(self, cadena, nombre):
        words = nombre.lower().split()
        rows = [(n, m, p) for n, m, p, c in self.products.values()
                if c == cadena and all(w in n.lower() for w in words)]
        return sorted(rows, key=lambda r: r[2])

    def realtime(self, cadena, nombre):
        return None


CATALOG = {
    "DIA_1": ("Leche Entera 1 L", "La Serenisima", 1500, "DIA"),
    "Coto_1": ("Leche Entera 1 L", "La Serenisima", 1300, "Coto"),
    "DIA_2": ("Arroz 500 g", "Gallo", 1000, "DIA"),
    "DIA_3": ("Arroz 1 kg", "Molinos", 1700, "DIA"),
    "Coto_2": ("Arroz 1 kg", "Gallo", 2000, "Coto"),
}


def test_specific_product_is_priced_per_chain():
    """Regresion: un producto elegido de DIA no debe sumar el precio de DIA en Coto."""
    res = optimize([{"producto_id": "DIA_1", "nombre": "leche", "cantidad": 2}], ["DIA", "Coto"], [],
                   FakeLookup(CATALOG))
    totals = {r["cadena"]: r["totalOriginal"] for r in res["rankingCadenas"]}
    assert totals == {"DIA": 3000, "Coto": 2600}
    assert res["cadenaRecomendada"] == "Coto"


def test_specific_product_missing_in_other_chain():
    res = optimize([{"producto_id": "DIA_2", "nombre": "arroz", "cantidad": 1}], ["DIA", "Coto"], [],
                   FakeLookup(CATALOG))
    assert [r["cadena"] for r in res["rankingCadenas"]] == ["DIA"]


def test_any_brand_by_weight_picks_cheapest_per_kilo():
    # 2 kg: DIA_2 (500 g) -> 4 x 1000 = 4000; DIA_3 (1 kg) -> 2 x 1700 = 3400
    res = optimize([{"producto_id": "cualquier_marca", "nombre": "arroz", "cantidad": 2, "unidad": "kg"}],
                   ["DIA"], [], FakeLookup(CATALOG))
    sel = res["productosSeleccionados"][0]
    assert sel["marca"] == "Molinos" and sel["cantidad"] == 2 and res["totalOriginal"] == 3400


def test_promos_change_the_recommendation():
    promos = [("DIA", "Banco Nación", "Visa", 20, "Martes", None, 7)]
    productos = [{"producto_id": "cualquier_marca", "nombre": "leche", "cantidad": 1}]
    martes = optimize(productos, ["DIA", "Coto"], promos, FakeLookup(CATALOG), dia="Martes")
    lunes = optimize(productos, ["DIA", "Coto"], promos, FakeLookup(CATALOG), dia="Lunes")
    assert martes["cadenaRecomendada"] == "DIA" and martes["totalFinal"] == 1200
    assert lunes["cadenaRecomendada"] == "Coto"


def test_cadenas_filter_and_missing():
    productos = [{"producto_id": "cualquier_marca", "nombre": "leche", "cantidad": 1},
                 {"producto_id": "cualquier_marca", "nombre": "fernet", "cantidad": 1}]
    res = optimize(productos, ["DIA", "Coto"], [], FakeLookup(CATALOG), cadenas_filter=["DIA"])
    assert [r["cadena"] for r in res["rankingCadenas"]] == ["DIA"]
    assert res["productosFaltantes"] == ["fernet"]


def test_empty_result():
    res = optimize([], ["DIA"], [], FakeLookup(CATALOG))
    assert res["cadenaRecomendada"] is None and res["rankingCadenas"] == []
