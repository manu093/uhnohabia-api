"""Tests de la API sin DB ni Firebase: version, y que los endpoints sensibles fallen cerrado."""
import json

import pytest
from fastapi.testclient import TestClient

import main
from api import config
from api.routers import auth, catalog, voice


@pytest.fixture
def client():
    with TestClient(main.app) as c:
        yield c


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_version_matches_version_json(client):
    data = json.loads((config.ROOT_DIR / "version.json").read_text(encoding="utf-8"))
    body = client.get("/app/version").json()
    assert body["versionCode"] == data["versionCode"]
    assert body["versionName"] == data["versionName"]
    assert body["apkUrl"].endswith(f"/v{data['versionName']}/UhNoHabia.apk")


def test_secret_matches():
    assert config.secret_matches("abc", "abc")
    assert not config.secret_matches("abc", "abd")
    assert not config.secret_matches("", "")          # sin secreto configurado: nunca pasa
    assert not config.secret_matches(None, "abc")


@pytest.mark.parametrize("path,payload", [
    ("/auth/admin-reset", {"email": "a@b.com", "newPassword": "12345678", "admin_key": "uhnohabia-admin-2026"}),
    ("/catalog/scrape", None),
    ("/catalog/import_products", {"api_key": "uhnohabia-scraper-2026", "products": []}),
    ("/voice/google", {"queryResult": {"parameters": {"product": "leche"}}}),
])
def test_sensitive_endpoints_disabled_without_secret(client, monkeypatch, path, payload):
    for mod, name in [(auth, "ADMIN_KEY"), (catalog, "SCRAPER_KEY"), (voice, "GOOGLE_VOICE_KEY")]:
        monkeypatch.setattr(mod, name, "")
    r = client.post(path, json=payload) if payload is not None else client.post(path)
    assert r.status_code == 503


def test_old_public_default_keys_are_rejected(client, monkeypatch):
    monkeypatch.setattr(auth, "ADMIN_KEY", "un-secreto-real")
    monkeypatch.setattr(catalog, "SCRAPER_KEY", "otro-secreto")
    r = client.post("/auth/admin-reset", json={"email": "a@b.com", "newPassword": "12345678",
                                               "admin_key": "uhnohabia-admin-2026"})
    assert r.status_code == 401
    r = client.post("/catalog/import_products", json={"api_key": "uhnohabia-scraper-2026", "products": []})
    assert r.status_code == 401
    r = client.post("/catalog/scrape", headers={"X-Api-Key": "uhnohabia-scraper-2026"})
    assert r.status_code == 401


def test_google_voice_requires_header(client, monkeypatch):
    monkeypatch.setattr(voice, "GOOGLE_VOICE_KEY", "clave")
    assert client.post("/voice/google", json={}).status_code == 401
    r = client.post("/voice/google", json={}, headers={"X-Voice-Key": "clave"})
    assert r.status_code == 200  # Firebase no inicializado en tests -> respuesta amable


@pytest.mark.parametrize("uri,ok", [
    ("https://pitangui.amazon.com/api/skill/link/X", True),
    ("https://layla.amazon.com/api/skill/link/X", True),
    ("https://alexa.amazon.co.jp/api/skill/link/X", True),
    ("http://pitangui.amazon.com/x", False),
    ("https://evil.com/?amazon.com", False),
    ("https://amazon.com.evil.com/x", False),
    ("https://evilamazon.com/x", False),
    ("", False),
])
def test_alexa_redirect_whitelist(uri, ok):
    assert auth._is_amazon_redirect(uri) is ok


def test_login_page_rejects_foreign_redirect(client):
    assert client.get("/auth/login", params={"redirect_uri": "https://evil.com"}).status_code == 400
    r = client.get("/auth/login", params={"redirect_uri": "https://pitangui.amazon.com/x", "state": '"><script>'})
    assert r.status_code == 200 and "<script>" not in r.text.split("<form")[1]
