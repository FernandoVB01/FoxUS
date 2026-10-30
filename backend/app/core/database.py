from __future__ import annotations

from collections.abc import Generator
from typing import Any
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import get_settings


def build_engine(database_uri: str | None = None) -> Engine:
    settings = get_settings()
    uri = database_uri or settings.sqlalchemy_database_uri

    connect_args: dict[str, Any] = {}
    engine_kwargs: dict[str, Any] = {
        "pool_pre_ping": True,
        "future": True,
    }

    if uri.startswith("sqlite"):
        connect_args["check_same_thread"] = False
    elif uri.startswith("postgresql+psycopg"):
        # Compatible with both Supabase Session Pooler and Transaction Pooler
        connect_args["connect_timeout"] = 10
        connect_args["prepare_threshold"] = None
        engine_kwargs["pool_size"] = 5
        engine_kwargs["max_overflow"] = 5
        engine_kwargs["pool_recycle"] = 300

    return create_engine(uri, connect_args=connect_args, **engine_kwargs)


engine: Engine = build_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def check_database_health(target_engine: Engine | None = None) -> dict[str, Any]:
    """Execute `SELECT 1` without ever exposing connection strings or credentials."""
    active_engine = target_engine or engine
    try:
        with active_engine.connect() as conn:
            scalar = conn.execute(text("SELECT 1")).scalar_one()
        return {
            "connected": scalar == 1,
            "dialect": active_engine.dialect.name,
            "driver": active_engine.dialect.driver,
            "select_1": scalar,
        }
    except Exception as exc:
        # Sanitize error type only; never include raw URL or password
        return {
            "connected": False,
            "dialect": active_engine.dialect.name,
            "driver": active_engine.dialect.driver,
            "error_type": type(exc).__name__,
        }
