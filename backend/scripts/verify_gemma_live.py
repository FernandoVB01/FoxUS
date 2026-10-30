from __future__ import annotations

import json
import sys
from pathlib import Path
from fastapi.testclient import TestClient

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.core.config import get_settings
from app.main import app


def main() -> int:
    get_settings.cache_clear()
    settings = get_settings()
    headers = {"X-Demo-Token": settings.DEMO_ACCESS_TOKEN.get_secret_value().strip()}

    with TestClient(app) as client:
        health = client.get("/api/v1/health").json()
        tasks = client.get("/api/v1/tasks").json()
        slide_task = next(
            (t for t in tasks if "diapositiva" in t["title"].lower()),
            tasks[0],
        )

        # 1. Live Gemma 4 FOX Challenge ("Desafío Relámpago")
        challenge_resp = client.post(
            "/api/v1/agents/generate-mission",
            headers=headers,
            json={
                "task_id": slide_task["id"],
                "available_minutes": 5,
                "requested_mode": "challenge",
                "dispatch_to_device": True,
                "ignore_cooldown_for_demo": True,
            },
        )
        challenge_data = challenge_resp.json()

        # 2. Live Gemma 4 FOX BZZ ("La Mosca Traviesa")
        bzz_resp = client.post(
            "/api/v1/agents/generate-mission",
            headers=headers,
            json={
                "task_id": slide_task["id"],
                "available_minutes": 5,
                "requested_mode": "fox_bzz",
                "dispatch_to_device": True,
                "ignore_cooldown_for_demo": True,
            },
        )
        bzz_data = bzz_resp.json()

        trace_timeline = client.get(
            f"/api/v1/agents/traces/{challenge_data['trace_id']}"
        ).json()

    report = {
        "health_ai": health.get("ai"),
        "target_task": slide_task["title"],
        "fox_challenge_mission": {
            "trace_id": challenge_data["trace_id"],
            "is_simulated_ai": challenge_data["is_simulated_ai"],
            "context_summary": challenge_data["context"]["contextual_summary"],
            "microtask_title": challenge_data["microtask"]["title"],
            "lcd_short_title": challenge_data["microtask"]["lcd_short_title"],
            "microtask_description": challenge_data["microtask"]["description"],
            "estimated_seconds": challenge_data["microtask"]["estimated_seconds"],
            "model_name": challenge_data["microtask"]["model_name"],
            "hook_message": challenge_data["hook"]["message"],
        },
        "fox_bzz_mission": {
            "trace_id": bzz_data["trace_id"],
            "is_simulated_ai": bzz_data["is_simulated_ai"],
            "microtask_title": bzz_data["microtask"]["title"],
            "model_name": bzz_data["microtask"]["model_name"],
            "hook_type": bzz_data["hook"]["hook_type"],
            "hook_message": bzz_data["hook"]["message"],
            "sound_id": bzz_data["hook"]["sound_id"],
            "animation_id": bzz_data["hook"]["animation_id"],
        },
        "trace_verification": {
            "used_real_gemma": trace_timeline["used_real_gemma"],
            "total_duration_ms": trace_timeline["total_duration_ms"],
            "models_invoked": trace_timeline["models_invoked"],
            "steps": [
                {
                    "agent": s["agent_name"],
                    "model": s["model_name"],
                    "duration_ms": s["duration_ms"],
                    "status": s["status"],
                }
                for s in trace_timeline["steps"]
            ],
        },
    }
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
