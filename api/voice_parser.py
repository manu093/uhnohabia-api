"""Interpreta lo que se le dicta a Alexa: separa productos, detecta cantidades y unidades,
y sugiere categoria/emoji con el mismo diccionario que usa la app
(api/product_dictionary.json, exportado desde ProductClassifier.kt).

    "dos leches un kilo de papa pan y detergente"
      -> [Leches x2, Papa 1 Kg, Pan, Detergente]
"""
import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

_DICT_PATH = Path(__file__).with_name("product_dictionary.json")

OTROS = "Otros"


@dataclass(frozen=True)
class _Entry:
    tokens: tuple[str, ...]
    category: str
    emoji: str


@dataclass
class VoiceItem:
    name: str
    quantity: float = 1.0
    unit: str = "Unidad"
    category: str = OTROS
    emoji: str = ""


def normalize(text: str) -> str:
    text = text.lower().replace("ñ", "\x01")
    text = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")
    text = text.replace("\x01", "ñ")
    return re.sub(r"[^a-zñ0-9]+", " ", text).strip()


def _load_entries() -> list[_Entry]:
    data = json.loads(_DICT_PATH.read_text(encoding="utf-8"))
    return [_Entry(tuple(normalize(k).split()), e["category"], e["emoji"]) for e in data for k in e["keywords"]]


_ENTRIES = _load_entries()


def _same_word(word: str, kw: str) -> bool:
    return (word == kw or word == kw + "s" or word == kw + "es"
            or (kw.endswith("z") and word == kw[:-1] + "ces")
            or (kw.endswith("s") and word == kw[:-1]))


def _matches(words: list[str]) -> list[tuple[int, int, _Entry]]:
    """Coincidencias (inicio, fin, entrada) sin solapamiento, priorizando frases largas."""
    found = []
    for e in _ENTRIES:
        n = len(e.tokens)
        for i in range(len(words) - n + 1):
            if all(_same_word(words[i + j], e.tokens[j]) for j in range(n)):
                found.append((i, i + n, e))
    found.sort(key=lambda m: (-(m[1] - m[0]), m[0]))
    taken: set[int] = set()
    result = []
    for start, end, e in found:
        if any(k in taken for k in range(start, end)):
            continue
        taken.update(range(start, end))
        result.append((start, end, e))
    return sorted(result, key=lambda m: m[0])


def classify(name: str) -> tuple[str, str] | None:
    """(categoria, emoji) para un nombre: gana la frase mas larga y, a igual largo, la primera."""
    words = normalize(name).split()
    best = None
    for e in _ENTRIES:
        n = len(e.tokens)
        for i in range(len(words) - n + 1):
            if all(_same_word(words[i + j], e.tokens[j]) for j in range(n)):
                if best is None or n > best[0] or (n == best[0] and i < best[1]):
                    best = (n, i, e)
                break
    return (best[2].category, best[2].emoji) if best else None


# --- Cantidades ------------------------------------------------------------------------

_NUMBERS = {
    "un": 1, "una": 1, "uno": 1, "dos": 2, "tres": 3, "cuatro": 4, "cinco": 5, "seis": 6, "siete": 7,
    "ocho": 8, "nueve": 9, "diez": 10, "once": 11, "doce": 12, "quince": 15, "veinte": 20,
    "medio": 0.5, "media": 0.5, "cuarto": 0.25,
}
_UNITS = {
    "kg": "Kg", "kilo": "Kg", "kilos": "Kg", "kilogramo": "Kg", "kilogramos": "Kg",
    "g": "Gr", "gr": "Gr", "gramo": "Gr", "gramos": "Gr",
    "litro": "Litro", "litros": "Litro", "l": "Litro",
    "docena": "Docena", "docenas": "Docena",
    "paquete": "Unidad", "paquetes": "Unidad", "caja": "Unidad", "cajas": "Unidad", "lata": "Unidad",
    "latas": "Unidad", "botella": "Unidad", "botellas": "Unidad", "pack": "Unidad", "packs": "Unidad",
    "bolsa": "Unidad", "bolsas": "Unidad", "frasco": "Unidad", "frascos": "Unidad",
    "unidad": "Unidad", "unidades": "Unidad", "sachet": "Unidad", "sachets": "Unidad",
    # Abreviaturas que devuelve el reconocimiento de voz de Alexa ("dos kilos" -> "2k")
    "k": "Kg", "kgs": "Kg", "grs": "Gr", "lt": "Litro", "lts": "Litro", "ml": "ml", "cc": "ml",
}
# "2k", "2kg", "500g", "1l", "750cc" -> "2 k", "2 kg", ...
_GLUED_UNIT = re.compile(r"\b(\d+(?:[.,]\d+)?)(kgs?|k|grs?|g|lts?|l|ml|cc)\b")
# Palabras que unen partes de un mismo producto ("jugo de naranja", "yogur con cereales")
_CONNECTORS = {"de", "del", "con", "sin", "en", "para", "a", "al", "la", "el", "las", "los", "x", "por"}
_ARTICLES = {"el", "la", "los", "las", "lo", "unos", "unas", "mi", "mis", "algo", "algun", "alguna"}
# Palabras de relleno que Alexa suele dejar en el slot
_FILLER = {"tambien", "ademas", "despues", "porfa", "por favor", "favor", "agrega", "agregar", "anota",
           "suma", "pone", "poneme", "necesito", "comprar", "quiero", "che", "bueno", "y", "e"}
_SEPARATORS = re.compile(r"\s*(?:,|;|\by\b|\be\b|\bmas\b|\btambien\b|\bademas\b|\bdespues\b)\s*")


def _is_number(w: str) -> bool:
    return w in _NUMBERS or bool(re.fullmatch(r"\d+(?:[.,]\d+)?", w))


def _number(w: str) -> float:
    return float(_NUMBERS[w]) if w in _NUMBERS else float(w.replace(",", "."))


def _split_chunk(words: list[str]) -> list[list[str]]:
    """Corta una frase sin separadores en varios productos:
    - un numero que no sigue a un conector empieza un producto nuevo ("leche dos panes");
    - dos productos conocidos pegados ("leche pan huevos") se separan, salvo que sean
      la misma familia ("fideos tirabuzon") o esten unidos por un conector."""
    if not words:
        return []
    cuts = set()
    for i in range(1, len(words)):
        if _is_number(words[i]) and words[i - 1] not in _CONNECTORS and not _is_number(words[i - 1]):
            # "coca de dos litros": el numero va unido por "de"; "2 2" no corta.
            # "papa dos kilos": si despues solo viene una unidad, es la cantidad del anterior.
            tail = words[i + 1:]
            trailing_qty = not tail or (len(tail) == 1 and tail[0] in _UNITS)
            if words[i - 1] not in _UNITS and not trailing_qty:
                cuts.add(i)
    matches = _matches(words)
    for (s1, e1, m1), (s2, _e2, m2) in zip(matches, matches[1:]):
        between = words[e1:s2]
        if m1.emoji == m2.emoji and m1.category == m2.category:
            continue
        if any(w in _CONNECTORS for w in between) or (s2 > 0 and words[s2 - 1] in _CONNECTORS):
            continue
        # el corte va justo antes del segundo producto (o de su cantidad, si la tiene)
        cut = s2
        while cut - 1 >= e1 and (_is_number(words[cut - 1]) or words[cut - 1] in _UNITS):
            cut -= 1
        if cut > 0:
            cuts.add(cut)
    bounds = [0] + sorted(cuts) + [len(words)]
    return [words[a:b] for a, b in zip(bounds, bounds[1:]) if words[a:b]]


def _to_item(words: list[str], original: dict[str, str]) -> VoiceItem | None:
    words = [w for w in words if w not in _FILLER]
    qty, unit = 1.0, "Unidad"
    i = 0
    if i < len(words) and _is_number(words[i]):
        qty = _number(words[i])
        i += 1
        # "un cuarto kilo", "medio kilo"
        if i < len(words) and words[i] in _UNITS:
            unit = _UNITS[words[i]]
            i += 1
        if i < len(words) and words[i] in ("de", "del"):
            i += 1
    elif i < len(words) and words[i] in _UNITS and words[i] not in ("unidad", "unidades"):
        # "kilo de papa" (sin numero) -> 1 Kg
        unit = _UNITS[words[i]]
        i += 1
        if i < len(words) and words[i] in ("de", "del"):
            i += 1
    rest = words[i:]
    # cantidad al final: "papa dos kilos", "leche por dos"
    if len(rest) >= 2 and rest[-1] in _UNITS and _is_number(rest[-2]):
        qty, unit = _number(rest[-2]), _UNITS[rest[-1]]
        rest = rest[:-2]
    elif len(rest) >= 2 and _is_number(rest[-1]) and rest[-2] in ("x", "por"):
        qty = _number(rest[-1])
        rest = rest[:-2]
    while rest and rest[-1] in _CONNECTORS:
        rest = rest[:-1]
    if unit == "ml":  # la app no tiene ml
        qty, unit = qty / 1000.0, "Litro"
    # "la leche", "los tomates", "unos fideos"
    while len(rest) > 1 and rest[0] in _ARTICLES:
        rest = rest[1:]
    if not rest or not any(c.isalpha() for c in "".join(rest)):
        return None
    # Recuperar acentos y ñ del texto original cuando se puede
    name = " ".join(original.get(w, w) for w in rest)
    name = name[0].upper() + name[1:]
    found = classify(name)
    category, emoji = found if found else (OTROS, "")
    return VoiceItem(name=name, quantity=qty, unit=unit, category=category, emoji=emoji)


def parse_products(text: str) -> list[VoiceItem]:
    """Convierte lo dictado en una lista de productos."""
    if not text or not text.strip():
        return []
    # mapa palabra normalizada -> palabra original (para conservar "jabón", "ñoquis")
    original = {}
    for w in re.findall(r"[\wáéíóúüñÁÉÍÓÚÜÑ]+", text):
        original.setdefault(normalize(w), w.lower())
    items: list[VoiceItem] = []
    for chunk in _SEPARATORS.split(_GLUED_UNIT.sub(r"\1 \2", normalize(text))):
        for words in _split_chunk(chunk.split()):
            item = _to_item(words, original)
            if item:
                items.append(item)
    return items
