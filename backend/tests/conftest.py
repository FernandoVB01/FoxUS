from __future__ import annotations

from collections.abc import Generator
import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import Settings, get_settings
from app.core.database import get_db
from app.main import create_app
from app.models import Base

TEST_DEMO_TOKEN = "test-demo-secret-token"
TEST_DEVICE_TOKEN = "test-device-secret-token"


def get_test_settings() -> Settings:
    return Settings(
        _env_file=None,
        APP_ENV="test",
        DATABASE_URL=SecretStr("sqlite+pysqlite:///:memory:"),
        GEMINI_API_KEY=SecretStr(""),
        GEMMA_MODEL="gemma-4-26b-a4b-it",
        DEMO_MODE=True,
        DEMO_ACCESS_TOKEN=SecretStr(TEST_DEMO_TOKEN),
        DEVICE_SHARED_TOKEN=SecretStr(TEST_DEVICE_TOKEN),
        MIN_INTERVENTION_COOLDOWN_SECONDS=15,
    )


@pytest.fixture()
def db_session() -> Generator[Session, None, None]:
    """Isolated in-memory SQLite session strictly for fast unit/API tests (Section 3)."""
    test_engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
        future=True,
    )
    Base.metadata.create_all(bind=test_engine)
    TestingSessionLocal = sessionmaker(
        bind=test_engine, autoflush=False, autocommit=False, expire_on_commit=False
    )
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=test_engine)
        test_engine.dispose()


@pytest.fixture()
def client(db_session: Session, monkeypatch: pytest.MonkeyPatch) -> Generator[TestClient, None, None]:
    test_settings = get_test_settings()
    monkeypatch.setattr("app.core.config.get_settings", lambda: test_settings)
    monkeypatch.setattr("app.core.security.get_settings", lambda: test_settings)
    monkeypatch.setattr("app.main.get_settings", lambda: test_settings)
    monkeypatch.setattr("app.api.v1.system.get_settings", lambda: test_settings)
    monkeypatch.setattr("app.api.v1.demo.get_settings", lambda: test_settings)
    monkeypatch.setattr("app.services.gemma_client.get_settings", lambda: test_settings)
    monkeypatch.setattr("app.services.policy_engine.get_settings", lambda: test_settings)
    monkeypatch.setattr("app.services.reward_engine.get_settings", lambda: test_settings)
    monkeypatch.setattr(
        "app.api.v1.system.check_database_health",
        lambda: {"connected": True, "dialect": "postgresql", "driver": "psycopg", "select_1": 1},
    )

    application = create_app()

    def _override_get_db() -> Generator[Session, None, None]:
        yield db_session

    application.dependency_overrides[get_db] = _override_get_db
    application.dependency_overrides[get_settings] = lambda: test_settings
    with TestClient(application) as test_client:
        yield test_client


@pytest.fixture()
def auth_headers() -> dict[str, str]:
    return {"X-Demo-Token": TEST_DEMO_TOKEN}


@pytest.fixture()
def device_headers() -> dict[str, str]:
    return {"X-Device-Token": TEST_DEVICE_TOKEN}
