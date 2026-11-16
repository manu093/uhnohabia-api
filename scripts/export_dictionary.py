"""Exporta el diccionario de ProductClassifier.kt (fuente unica) a api/product_dictionary.json.

Uso:  python scripts/export_dictionary.py          -> regenera el JSON
      python scripts/export_dictionary.py --check  -> falla si el JSON esta desactualizado
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KT = ROOT / "app/src/main/java/com/sharedshoppinglists/app/domain/classifier/ProductClassifier.kt"
OUT = ROOT / "api/product_dictionary.json"


def extract() -> list[dict]:
    src = KT.read_text(encoding="utf-8")
    consts = dict(re.findall(r'const val (\w+) = "([^"]+)"', src))
    block = src[src.index("private val DICTIONARY"):src.index("private val UNIT_WORDS")]
    entries = []
    for cat, emoji, args in re.findall(r'add\((\w+),\s*"([^"]+)",((?:\s*"[^"]*",?)+)\s*\)', block):
        keywords = re.findall(r'"([^"]*)"', args)
        entries.append({"category": consts[cat], "emoji": emoji, "keywords": keywords})
    if not entries:
        raise SystemExit("No se encontraron entradas en ProductClassifier.kt")
    return entries


def render(entries) -> str:
    return json.dumps(entries, ensure_ascii=False, indent=1) + "\n"


if __name__ == "__main__":
    content = render(extract())
    if "--check" in sys.argv:
        if OUT.read_text(encoding="utf-8") != content:
            raise SystemExit("api/product_dictionary.json desactualizado: corre python scripts/export_dictionary.py")
        print("ok")
    else:
        OUT.write_text(content, encoding="utf-8")
        print(f"{OUT.relative_to(ROOT)}: {len(json.loads(content))} entradas")
