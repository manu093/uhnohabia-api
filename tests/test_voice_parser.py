import subprocess
import sys
from pathlib import Path

import pytest

from api.voice_parser import classify, parse_products

ROOT = Path(__file__).resolve().parent.parent


def names(text):
    return [i.name for i in parse_products(text)]


def summary(text):
    return [(i.name, i.quantity, i.unit) for i in parse_products(text)]


@pytest.mark.parametrize("text,expected", [
    ("leche", ["Leche"]),
    ("leche pan y huevos", ["Leche", "Pan", "Huevos"]),
    ("leche, pan, huevos", ["Leche", "Pan", "Huevos"]),
    ("leche pan huevos yerba", ["Leche", "Pan", "Huevos", "Yerba"]),
    ("pan lactal y queso rallado", ["Pan lactal", "Queso rallado"]),
    ("jugo de naranja y galletitas de chocolate", ["Jugo de naranja", "Galletitas de chocolate"]),
    ("fideos tirabuzón", ["Fideos tirabuzón"]),
    ("dulce de leche", ["Dulce de leche"]),
    ("papel higiénico detergente y lavandina", ["Papel higiénico", "Detergente", "Lavandina"]),
    ("coca cola y cerveza", ["Coca cola", "Cerveza"]),
    ("jabón en polvo", ["Jabón en polvo"]),
    ("leche también pan", ["Leche", "Pan"]),
])
def test_split_products(text, expected):
    assert names(text) == expected


@pytest.mark.parametrize("text,expected", [
    ("dos leches", [("Leches", 2, "Unidad")]),
    ("2 leches", [("Leches", 2, "Unidad")]),
    ("un kilo de papa", [("Papa", 1, "Kg")]),
    ("dos kilos de papas y una docena de huevos", [("Papas", 2, "Kg"), ("Huevos", 1, "Docena")]),
    ("medio kilo de queso", [("Queso", 0.5, "Kg")]),
    ("media docena de huevos", [("Huevos", 0.5, "Docena")]),
    ("dos leches un kilo de papa pan y detergente",
     [("Leches", 2, "Unidad"), ("Papa", 1, "Kg"), ("Pan", 1, "Unidad"), ("Detergente", 1, "Unidad")]),
    ("leche dos panes", [("Leche", 1, "Unidad"), ("Panes", 2, "Unidad")]),
    ("papa dos kilos", [("Papa", 2, "Kg")]),
    ("tres paquetes de fideos", [("Fideos", 3, "Unidad")]),
    ("500 gramos de carne picada", [("Carne picada", 500, "Gr")]),
    ("kilo de asado", [("Asado", 1, "Kg")]),
    ("cerveza por seis", [("Cerveza", 6, "Unidad")]),
])
def test_quantities(text, expected):
    assert summary(text) == expected


def test_categories_and_emojis():
    items = {i.name: i for i in parse_products("leche, tomates, asado, lavandina, shampoo y fernet")}
    assert (items["Leche"].category, items["Leche"].emoji) == ("Granja", "🥛")
    assert items["Tomates"].category == "Verdulería"
    assert items["Asado"].category == "Carnicería"
    assert items["Lavandina"].category == "Limpieza"
    assert items["Shampoo"].category == "Perfumería"
    assert items["Fernet"].category == "Bebidas"


def test_unknown_product_goes_to_otros():
    [item] = parse_products("regalo para la abuela")
    assert item.category == "Otros" and item.emoji == ""


def test_empty_and_filler():
    assert parse_products("") == []
    assert parse_products("   ") == []
    assert parse_products("y") == []


def test_classify_matches_app_rules():
    assert classify("dulce de leche") == ("Almacén", "🍯")
    assert classify("salsa de tomate")[0] == "Almacén"
    assert classify("crema dental")[0] == "Perfumería"


def test_dictionary_in_sync_with_android():
    """api/product_dictionary.json tiene que coincidir con ProductClassifier.kt."""
    kt = ROOT / "app/src/main/java/com/sharedshoppinglists/app/domain/classifier/ProductClassifier.kt"
    if not kt.exists():
        pytest.skip("codigo Android no disponible")
    r = subprocess.run([sys.executable, str(ROOT / "scripts/export_dictionary.py"), "--check"],
                       capture_output=True, text=True)
    assert r.returncode == 0, r.stderr or r.stdout
