"""Endpoints generales: salud, version para OTA y paginas legales."""
from fastapi import APIRouter
from fastapi.responses import HTMLResponse, RedirectResponse

from api.config import load_version

router = APIRouter()

PRIVACY_HTML = "<html><head><meta charset=utf-8><meta name=viewport content=width=device-width,initial-scale=1><title>Privacidad - Uh No Habia</title><style>body{font-family:system-ui;max-width:700px;margin:40px auto;padding:0 20px;line-height:1.6}h1{color:#FF6B6B}h2{color:#4ECDC4}</style></head><body><h1>Politica de Privacidad</h1><p><b>Uh No Habia</b> - Abril 2026</p><h2>Datos que recopilamos</h2><ul><li>Email y nombre para autenticacion</li><li>Listas de compras y productos</li><li>Preferencias de tema y medios de pago</li><li>Comandos de voz via Alexa</li></ul><h2>Uso de datos</h2><ul><li>Sincronizar listas entre dispositivos</li><li>Compartir listas con otros usuarios</li><li>Optimizar precios via Precios Claros (SEPA)</li><li>Procesar comandos de voz</li></ul><h2>Almacenamiento</h2><ul><li>Firebase Firestore (Google Cloud)</li><li>Base de datos local en tu dispositivo</li><li>No vendemos ni compartimos datos con terceros</li></ul><h2>Alexa</h2><ul><li>Amazon procesa tu voz y nos envia el texto</li><li>Solo recibimos el nombre del producto</li><li>No almacenamos grabaciones de voz</li></ul><h2>Tus derechos</h2><ul><li>Podes eliminar tu cuenta y datos desde la app</li><li>Podes exportar listas como texto o CSV</li><li>Podes desvincular Alexa desde la app de Amazon</li></ul><h2>Contacto</h2><p>uhnohabia@gmail.com</p></body></html>"
TERMS_HTML = "<html><head><meta charset=utf-8><meta name=viewport content=width=device-width,initial-scale=1><title>Terminos - Uh No Habia</title><style>body{font-family:system-ui;max-width:700px;margin:40px auto;padding:0 20px;line-height:1.6}h1{color:#FF6B6B}h2{color:#4ECDC4}</style></head><body><h1>Terminos de Uso</h1><p><b>Uh No Habia</b> - Abril 2026</p><h2>Uso</h2><p>App gratuita para listas de compras compartidas y comparar precios en Argentina.</p><h2>Precios</h2><p>Los precios provienen de Precios Claros (SEPA). No garantizamos exactitud.</p><h2>Cuenta</h2><p>Necesitas email para usar la app.</p><h2>Listas compartidas</h2><p>Al compartir, otros pueden ver y modificar productos.</p><h2>Contacto</h2><p>uhnohabia@gmail.com</p></body></html>"


@router.get("/health")
async def health():
    return {"status": "ok"}


@router.get("/app/version")
async def app_version():
    """Version actual de la app para actualizaciones OTA (lee version.json)."""
    return load_version()


@router.get("/app/download")
async def app_download():
    return RedirectResponse(load_version()["apkUrl"])


@router.get("/privacy", response_class=HTMLResponse)
async def privacy_policy():
    return PRIVACY_HTML


@router.get("/terms", response_class=HTMLResponse)
async def terms_of_use():
    return TERMS_HTML
