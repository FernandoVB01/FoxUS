from __future__ import annotations

from datetime import UTC, datetime, timedelta
from fastapi.testclient import TestClient

from app.integrations.calendar_provider import ICalFeedCalendarProvider


def test_patch_preferences_ignores_explicit_null_non_nullable_fields(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    """Regression test for explicit null fields sent by mobile clients on PATCH /preferences."""
    resp = client.patch(
        "/api/v1/preferences",
        headers=auth_headers,
        json={
            "intervention_level": 3,
            "preferred_hook": None,
            "audio_enabled": True,
            "music_enabled": False,
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["intervention_level"] == 3
    assert body["preferred_hook"] == "auto"


def test_proactive_pressure_evaluation_and_bullet_steps(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    created = client.post(
        "/api/v1/tasks",
        headers=auth_headers,
        json={
            "title": "Proyecto final de Sistemas Distribuidos (12 diapositivas)",
            "description": "Entrega crítica mañana rano; admito que necesito presión.",
            "needs_pressure": True,
            "deadline": (datetime.now(UTC) + timedelta(hours=5)).isoformat(),
        },
    )
    assert created.status_code == 201
    task_id = created.json()["id"]
    assert created.json()["needs_pressure"] is True

    # Evaluate pressure proactively
    eval_resp = client.post(
        "/api/v1/agents/evaluate-pressure",
        headers=auth_headers,
        json={"task_id": task_id, "demo_force": True},
    )
    assert eval_resp.status_code == 200
    data = eval_resp.json()
    assert data["should_interrupt"] is True
    assert data["urgency_score"] >= 70
    assert data["mission"] is not None
    steps = data["mission"]["microtask"]["steps"]
    assert isinstance(steps, list)
    assert len(steps) >= 2


def test_google_status_and_ics_calendar_parser(client: TestClient) -> None:
    status_resp = client.get("/api/v1/integrations/google/status")
    assert status_resp.status_code == 200
    assert status_resp.json()["connected"] is False

    now = datetime.now(UTC)
    ev_start = (now + timedelta(hours=2)).strftime("%Y%m%dT%H%M%SZ")
    ev_end = (now + timedelta(hours=3)).strftime("%Y%m%dT%H%M%SZ")
    sample_ics = (
        "BEGIN:VCALENDAR\r\n"
        "VERSION:2.0\r\n"
        "BEGIN:VEVENT\r\n"
        "UID:foxus-demo-ev-1@google.com\r\n"
        "SUMMARY:Exposición Hacktoberfest ESPOL\r\n"
        f"DTSTART:{ev_start}\r\n"
        f"DTEND:{ev_end}\r\n"
        "END:VEVENT\r\n"
        "END:VCALENDAR\r\n"
    )
    parsed = ICalFeedCalendarProvider.parse_ics_events(sample_ics, horizon_hours=24)
    assert len(parsed) == 1
    assert parsed[0].title == "Exposición Hacktoberfest ESPOL"
    assert parsed[0].source == "google_calendar_ics"


def test_referee_agent_voice_and_photo_evidence_verification(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    created = client.post(
        "/api/v1/tasks",
        headers=auth_headers,
        json={"title": "Redactar introducción del artículo técnico", "needs_pressure": True},
    )
    task_id = created.json()["id"]

    mission_resp = client.post(
        "/api/v1/agents/generate-mission",
        headers=auth_headers,
        json={"task_id": task_id, "requested_mode": "challenge", "ignore_cooldown_for_demo": True},
    )
    assert mission_resp.status_code == 200
    mission = mission_resp.json()
    assert len(mission["hook"]["coach_voice_lines"]) == 3
    assert mission["hook"]["ignition_minigame"] in ("catch_fox", "spark_combo")

    start_resp = client.post(
        "/api/v1/sessions/start",
        headers=auth_headers,
        json={"microtask_id": mission["microtask_id"], "hook_id": mission["hook_id"]},
    )
    assert start_resp.status_code == 200
    session_id = start_resp.json()["session"]["id"]

    verify_resp = client.post(
        f"/api/v1/sessions/{session_id}/verify-evidence",
        headers=auth_headers,
        json={
            "evidence_type": "voice",
            "voice_transcript": "Abrí el documento y escribí el título y las primeras cuatro líneas de la introducción.",
            "checked_steps_count": 3,
            "actual_duration_seconds": 175,
        },
    )
    assert verify_resp.status_code == 200
    v_body = verify_resp.json()
    assert v_body["verdict"]["verified"] is True
    assert v_body["bonus_xp_awarded"] == 15
    assert len(v_body["verdict"]["referee_speech"]) > 5
