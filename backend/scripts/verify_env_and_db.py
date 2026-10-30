from __future__ import annotations

import json
import sys
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import alembic
import fastapi
import google.genai
import psycopg
import pydantic
import pydantic_settings
import sqlalchemy

from sqlalchemy import text
from app.core.database import check_database_health, engine


def main() -> int:
    versions = {
        "python": sys.version.split()[0],
        "fastapi": fastapi.__version__,
        "pydantic": pydantic.__version__,
        "pydantic_settings": pydantic_settings.__version__,
        "sqlalchemy": sqlalchemy.__version__,
        "alembic": alembic.__version__,
        "psycopg": psycopg.__version__,
        "google_genai": getattr(google.genai, "__version__", "installed"),
    }
    db_status = check_database_health()
    public_tables: list[str] = []
    if db_status.get("connected"):
        with engine.connect() as conn:
            rows = conn.execute(
                text(
                    "SELECT table_name FROM information_schema.tables "
                    "WHERE table_schema = 'public' ORDER BY table_name"
                )
            ).scalars()
            public_tables = list(rows)
    report = {
        "imports_ok": True,
        "versions": versions,
        "database_health": db_status,
        "public_tables": public_tables,
    }
    print(json.dumps(report, indent=2))
    return 0 if db_status.get("connected") is True else 1


if __name__ == "__main__":
    raise SystemExit(main())
