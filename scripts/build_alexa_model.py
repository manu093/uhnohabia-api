"""Genera alexa/interactionModel.json a partir del diccionario de productos.

Uso: python scripts/build_alexa_model.py
Despues pegar el JSON en developer.amazon.com > tu skill > Build > Interaction Model > JSON Editor
y tocar "Save Model" + "Build Model".
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DICT = ROOT / "api/product_dictionary.json"
OUT = ROOT / "alexa/interactionModel.json"

INVOCATION = "mi super"  # "abrir super" no se permite: "abrir" es palabra de arranque

ADD_VERBS = ["agregá", "agrega", "agregar", "agregame", "anotá", "anota", "anotame", "sumá", "suma", "sumame",
             "poné", "pone", "poneme", "añadí", "añade", "necesito", "quiero", "comprar", "hay que comprar",
             "falta", "faltan", "me falta", "me faltan", "se terminó", "se terminaron", "se acabó", "traer",
             "acordate de", "acordate de comprar", "no te olvides de",
             # frases de un solo paso: "Alexa, pedile a mi mercado que agregue leche"
             "que agregue", "que agregues", "que anote", "que anotes", "que sume", "que sumes", "que ponga",
             "que pongas", "agregue", "anote", "sume", "ponga"]
ADD_TO_LIST = ["agregá {product} a la lista {listName}", "agregá {product} a la lista de {listName}",
               "anotá {product} en la lista {listName}", "anotá {product} en la lista de {listName}",
               "poné {product} en la lista {listName}", "sumá {product} a la lista {listName}",
               "agrega {product} a la lista {listName}", "anota {product} en la lista {listName}",
               "en la lista {listName} agregá {product}", "en la lista de {listName} anotá {product}"]

READ = ["qué hay en la lista", "qué hay en mi lista", "qué tengo en la lista", "qué tengo en mi lista",
        "leeme la lista", "leé la lista", "lee la lista", "decime la lista", "decime qué hay en la lista",
        "qué me falta", "qué me falta comprar", "qué falta comprar", "qué tengo que comprar",
        "qué hay que comprar", "repasame la lista", "qué anoté",
        "que me lea la lista", "que me leas la lista", "que me diga qué falta", "que me digas qué falta",
        "qué hay en la lista {listName}", "qué hay en la lista de {listName}", "leeme la lista {listName}",
        "leeme la lista de {listName}", "qué falta en la lista {listName}", "qué me falta de {listName}"]

REMOVE_VERBS = ["sacá", "saca", "sacame", "borrá", "borra", "borrame", "quitá", "quita", "eliminá", "elimina",
                "tachá", "tacha", "ya no necesito", "no hace falta",
                "que saque", "que saques", "que borre", "que borres", "que quite", "que quites"]
REMOVE_FROM_LIST = ["sacá {product} de la lista {listName}", "borrá {product} de la lista {listName}",
                    "quitá {product} de la lista {listName}", "sacá {product} de la lista de {listName}"]

SELECT = ["usá la lista {listName}", "usa la lista {listName}", "usar la lista {listName}",
          "cambiá a la lista {listName}", "cambia a la lista {listName}", "cambiá la lista a {listName}",
          "elegí la lista {listName}", "elige la lista {listName}", "quiero usar la lista {listName}",
          "trabajá con la lista {listName}", "andá a la lista {listName}", "abrí la lista {listName}",
          "pasá a la lista {listName}", "que use la lista {listName}", "usá la lista de {listName}",
          "cambiá a la lista de {listName}", "cambiar de lista", "cambiá de lista", "elegir lista",
          "usá otra lista", "quiero cambiar de lista"]
LIST_LISTS = ["qué listas tengo", "cuáles son mis listas", "decime mis listas", "qué listas hay", "mis listas",
              "que me diga mis listas", "qué listas tengo creadas", "en qué lista estoy", "qué lista estás usando"]

# Ejemplos de frases completas: ayudan a que Alexa capture varios productos y cantidades juntos
MULTI_EXAMPLES = [
    "leche pan y huevos", "dos leches", "un kilo de papa", "medio kilo de queso", "una docena de huevos",
    "dos kilos de tomate y una lechuga", "yerba azúcar y café", "pan lactal y queso rallado",
    "fideos arroz y puré de tomate", "papel higiénico y detergente", "tres cervezas y una coca",
    "carne picada y pollo", "jabón en polvo y suavizante", "dulce de leche", "galletitas de chocolate",
    "un paquete de fideos", "dos litros de leche", "seis huevos", "una lavandina", "shampoo y acondicionador",
]

LIST_NAMES = ["super", "supermercado", "compras", "compras del mes", "mercado", "verdulería", "farmacia",
              "carnicería", "casa", "la semana", "mi lista", "fiesta", "asado", "oficina", "chino", "almacén", "prueba",
              "limpieza", "cumpleaños", "vacaciones", "regalos", "mayorista", "kiosco"]


def build() -> dict:
    entries = json.loads(DICT.read_text(encoding="utf-8"))
    values = []
    seen = set()
    for v in [k for e in entries for k in e["keywords"]] + MULTI_EXAMPLES:
        # Alexa prefiere los numeros escritos en letras en los valores de slot ("7up" se omite)
        if v not in seen and not any(ch.isdigit() for ch in v):
            seen.add(v)
            values.append({"name": {"value": v}})

    # Si la frase empieza con un numero ("dos kilos de papa"), Alexa lo separa del slot de texto:
    # se captura en {quantity} y el backend lo vuelve a unir.
    qty_verbs = ["agregá", "agrega", "anotá", "anota", "sumá", "poné", "necesito", "quiero", "comprar",
                 "que agregue", "que anote", "me faltan"]
    add_samples = (["{product}", "{quantity} {product}"] + [f"{v} {{product}}" for v in ADD_VERBS]
                   + [f"{v} {{quantity}} {{product}}" for v in qty_verbs] + ADD_TO_LIST
                   + ["agregá {quantity} {product} a la lista {listName}", "anotá {quantity} {product} en la lista {listName}"])
    remove_samples = [f"{v} {{product}}" for v in REMOVE_VERBS] + REMOVE_FROM_LIST

    return {
        "interactionModel": {
            "languageModel": {
                "invocationName": INVOCATION,
                "intents": [
                    {"name": "AddProductIntent",
                     "slots": [{"name": "product", "type": "PRODUCT_NAME"}, {"name": "listName", "type": "LIST_NAME"},
                               {"name": "quantity", "type": "AMAZON.NUMBER"}],
                     "samples": add_samples},
                    {"name": "ReadListIntent", "slots": [{"name": "listName", "type": "LIST_NAME"}],
                     "samples": READ},
                    {"name": "RemoveProductIntent",
                     "slots": [{"name": "product", "type": "PRODUCT_NAME"}, {"name": "listName", "type": "LIST_NAME"}],
                     "samples": remove_samples},
                    {"name": "SelectListIntent", "slots": [{"name": "listName", "type": "LIST_NAME"}],
                     "samples": SELECT},
                    {"name": "ListListsIntent", "samples": LIST_LISTS},
                    {"name": "AMAZON.HelpIntent", "samples": ["ayuda", "qué puedo decir", "cómo funciona", "cómo se usa"]},
                    {"name": "AMAZON.StopIntent", "samples": ["nada más", "eso es todo", "listo", "chau", "ya está",
                                                              "terminé", "nada más gracias"]},
                    {"name": "AMAZON.CancelIntent", "samples": []},
                    {"name": "AMAZON.YesIntent", "samples": []},
                    {"name": "AMAZON.NoIntent", "samples": ["no gracias"]},
                    {"name": "AMAZON.FallbackIntent", "samples": []},
                    {"name": "AMAZON.NavigateHomeIntent", "samples": []},
                ],
                "types": [
                    {"name": "PRODUCT_NAME", "values": values},
                    {"name": "LIST_NAME", "values": [{"name": {"value": v}} for v in LIST_NAMES]},
                ],
            }
        }
    }


if __name__ == "__main__":
    model = build()
    OUT.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lm = model["interactionModel"]["languageModel"]
    print(f"{OUT.relative_to(ROOT)}: {len(lm['intents'])} intents, "
          f"{len(lm['types'][0]['values'])} valores de producto")
