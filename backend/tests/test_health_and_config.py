from __future__ import annotations

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.agents.contracts import format_lcd_line
from app.core.config import Settings, normalize_database_url
from app.services.gemma_client import extract_json_object


def test_health_and_public_config(client: TestClient) -> None:
    health = client.get("/api/v1/health")
    assert health.status_code == 200
    body = health.json()
    assert body["status"] == "ok"
    assert body["database"]["select_1"] == 1
    assert body["ai"]["configured_model"] == "gemma-4-26b-a4b-it"

    pub = client.get("/api/v1/config/public")
    assert pub.status_code == 200
    pub_body = pub.json()
    assert pub_body["gemma_model"] == "gemma-4-26b-a4b-it"
    mode_ids = {m["id"] for m in pub_body["interactive_modes"]}
    assert mode_ids == {"challenge", "fox_bzz"}


def test_non_gemma_model_rejected_by_settings() -> None:
    with pytest.raises(ValidationError):
        Settings(GEMMA_MODEL="gemini-2.5-pro")


def test_database_url_normalization() -> None:
    assert normalize_database_url("postgresql://u:p@host:5432/db").startswith(
        "postgresql+psycopg://"
    )
    assert normalize_database_url("postgres://u:p@host:5432/db").startswith(
        "postgresql+psycopg://"
    )


def test_extract_json_and_lcd_formatting() -> None:
    fenced = 'Aquí tienes:\n```json\n{"title": "Crear portada", "intensity": 2}\n```\n¡Éxito!'
    extracted = extract_json_object(fenced)
    assert extracted == '{"title": "Crear portada", "intensity": 2}'

    lcd_line = format_lcd_line("¡Diseñar presentación académica!", 16)
    assert len(lcd_line) <= 16
    assert lcd_line.isascii()
