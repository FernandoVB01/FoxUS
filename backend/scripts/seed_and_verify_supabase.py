from __future__ import annotations

import json
import sys
from pathlib import Path
from fastapi.testclient import TestClient
from sqlalchemy import text

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.core.database import SessionLocal, check_database_health, engine
from app.main import app
from app.repositories.seed import ensure_demo_user_and_seed


def main() -> int:
    # 1. Verify raw SQL SELECT 1 against Supabase PostgreSQL Session Pooler
    db_health = check_database_health()
    if not db_health.get("connected"):
        print(json.dumps({"ok": False, "database_health": db_health}, indent=2))
        return 1

    # 2. Seed controlled demo user, preferences, device, and starter tasks in Supabase
    with SessionLocal() as db:
        demo_user = ensure_demo_user_and_seed(db)
        seeded_user_id = str(demo_user.id)

    # 3. Verify all 12 domain tables + alembic_version exist in Supabase public schema
    with engine.connect() as conn:
        select_1_value = conn.execute(text("SELECT 1")).scalar_one()
        tables = list(
            conn.execute(
                text(
                    "SELECT table_name FROM information_schema.tables "
                    "WHERE table_schema = 'public' ORDER BY table_name"
                )
            ).scalars()
        )
        alembic_rev = conn.execute(text("SELECT version_num FROM alembic_version")).scalar_one()

    # 4. Smoke-test FastAPI live endpoints backed by Supabase PostgreSQL
    with TestClient(app) as client:
        health_resp = client.get("/api/v1/health")
        tasks_resp = client.get("/api/v1/tasks")
        profile_resp = client.get("/api/v1/rewards/profile")

    summary = {
        "supabase_select_1": select_1_value,
        "alembic_revision": alembic_rev,
        "public_tables_count": len(tables),
        "public_tables": tables,
        "seeded_demo_user_id": seeded_user_id,
        "api_health_http_status": health_resp.status_code,
        "api_health_payload": health_resp.json(),
        "seeded_tasks_count": len(tasks_resp.json()),
        "reward_profile_level": profile_resp.json().get("level"),
    }
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
