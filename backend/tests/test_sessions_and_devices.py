from __future__ import annotations

from fastapi.testclient import TestClient


def test_full_end_to_end_mission_and_esp32_button_flow(
    client: TestClient,
    auth_headers: dict[str, str],
    device_headers: dict[str, str],
) -> None:
    # 1. Unauthorized write rejected
    unauth = client.post("/api/v1/tasks", json={"title": "Tarea no autorizada"})
    assert unauth.status_code == 401

    # 2. Create manual task ("Preparar 12 diapositivas")
    create_resp = client.post(
        "/api/v1/tasks",
        headers=auth_headers,
        json={
            "title": "Preparar 12 diapositivas",
            "description": "Exposición de proyecto FOXUS para Clubs Royale.",
        },
    )
    assert create_resp.status_code == 201
    task_id = create_resp.json()["id"]

    # 3. Register ESP32 Watch device
    dev_resp = client.post(
        "/api/v1/devices/register",
        headers=device_headers,
        json={"device_name": "FOXUS ESP32 Lab", "device_type": "esp32_watch"},
    )
    assert dev_resp.status_code == 201
    device_id = dev_resp.json()["id"]

    # 4. Generate mission via Multi-Agent Orchestrator
    mission_resp = client.post(
        "/api/v1/agents/generate-mission",
        headers=auth_headers,
        json={
            "task_id": task_id,
            "available_minutes": 5,
            "requested_mode": "challenge",
            "dispatch_to_device": True,
            "ignore_cooldown_for_demo": True,
        },
    )
    assert mission_resp.status_code == 200
    mission = mission_resp.json()
    microtask_id = mission["microtask_id"]
    hook_id = mission["hook_id"]

    # 5. ESP32 polls pending command and receives 16-char LCD payload
    poll_resp = client.get(
        f"/api/v1/devices/{device_id}/commands/next",
        headers=device_headers,
    )
    assert poll_resp.status_code == 200
    poll_data = poll_resp.json()
    assert poll_data["has_command"] is True
    cmd = poll_data["command"]
    assert cmd["command_type"] == "SHOW_MISSION"
    assert len(cmd["payload"]["lcd_line_1"]) <= 16
    assert len(cmd["payload"]["lcd_line_2"]) <= 16

    # 6. ESP32 physical button ACCEPT starts session (+10 XP)
    accept_resp = client.post(
        f"/api/v1/devices/{device_id}/events",
        headers=device_headers,
        json={
            "event_type": "BUTTON_ACCEPT",
            "command_id": cmd["id"],
            "microtask_id": microtask_id,
            "hook_id": hook_id,
            "source": "esp32",
        },
    )
    assert accept_resp.status_code == 200
    accept_body = accept_resp.json()
    assert accept_body["xp_awarded"] == 10
    session_id = accept_body["session_id"]
    assert session_id is not None

    # 7. Duplicate network retry of BUTTON_ACCEPT does NOT duplicate XP
    dup_accept = client.post(
        f"/api/v1/devices/{device_id}/events",
        headers=device_headers,
        json={
            "event_type": "BUTTON_ACCEPT",
            "command_id": cmd["id"],
            "microtask_id": microtask_id,
            "hook_id": hook_id,
            "source": "esp32",
        },
    )
    assert dup_accept.status_code == 200
    assert dup_accept.json()["xp_awarded"] == 0

    # 8. ESP32 physical button COMPLETE marks session self-reported & grants +20 XP once
    comp_resp = client.post(
        f"/api/v1/devices/{device_id}/events",
        headers=device_headers,
        json={
            "event_type": "BUTTON_COMPLETE",
            "session_id": session_id,
            "microtask_id": microtask_id,
            "source": "esp32",
        },
    )
    assert comp_resp.status_code == 200
    comp_body = comp_resp.json()
    assert comp_body["xp_awarded"] == 20
    assert comp_body["total_xp"] == 30
    assert comp_body["lcd_line_1"] == "MISSION DONE!"

    # 9. Verify session completion_source is marked self-reported & Reward Profile matches
    hist = client.get("/api/v1/sessions/history").json()
    assert hist[0]["completion_source"] == "esp32_self_reported"

    profile = client.get("/api/v1/rewards/profile").json()
    assert profile["total_xp"] == 30
    assert profile["completed_microtasks_count"] == 1


def test_fox_bzz_hook_dismissal_persists_events_and_stats(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    task_resp = client.post(
        "/api/v1/tasks",
        headers=auth_headers,
        json={"title": "Estudiar capítulo 1 de algoritmos"},
    )
    assert task_resp.status_code == 201
    task_id = task_resp.json()["id"]

    bzz_resp = client.post(
        "/api/v1/agents/generate-mission",
        headers=auth_headers,
        json={
            "task_id": task_id,
            "requested_mode": "fox_bzz",
            "ignore_cooldown_for_demo": True,
        },
    )
    assert bzz_resp.status_code == 200
    bzz_mission = bzz_resp.json()
    assert bzz_mission["hook"]["hook_type"] == "fox_bzz"
    hook_id = bzz_mission["hook_id"]
    assert hook_id is not None

    dismiss_resp = client.post(
        f"/api/v1/sessions/hooks/{hook_id}/dismiss",
        headers=auth_headers,
        json={"source": "android", "reason": "silenced"},
    )
    assert dismiss_resp.status_code == 200
    dismiss_body = dismiss_resp.json()
    assert dismiss_body["status"] == "silenced"
    assert dismiss_body["recorded_event_type"] == "HOOK_SILENCED"

    profile = client.get("/api/v1/rewards/profile").json()
    bzz_stats = next(s for s in profile["hook_stats"] if s["hook_type"] == "fox_bzz")
    assert bzz_stats["shown_count"] >= 1
    assert bzz_stats["rejected_count"] >= 1
