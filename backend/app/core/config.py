from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parents[2]
ENV_FILE_PATH = BACKEND_DIR / ".env"


def normalize_database_url(raw_url: str) -> str:
    """Ensure PostgreSQL URLs use SQLAlchemy 2 + Psycopg 3 driver (`postgresql+psycopg://`)."""
    url = raw_url.strip()
    if url.startswith("postgres://"):
        return "postgresql+psycopg://" + url[len("postgres://") :]
    if url.startswith("postgresql://"):
        return "postgresql+psycopg://" + url[len("postgresql://") :]
    if url.startswith("postgresql+psycopg2://"):
        return "postgresql+psycopg://" + url[len("postgresql+psycopg2://") :]
    return url


class Settings(BaseSettings):
    """Centralized typed configuration loaded from environment / `backend/.env`."""

    model_config = SettingsConfigDict(
        env_file=str(ENV_FILE_PATH),
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",
    )

    APP_NAME: str = "FOXUS API"
    APP_VERSION: str = "0.2.0"
    APP_ENV: str = Field(default="development")
    API_V1_PREFIX: str = "/api/v1"

    DATABASE_URL: SecretStr = Field(
        default=SecretStr("postgresql+psycopg://foxus:password@localhost:5432/foxus")
    )

    GEMINI_API_KEY: SecretStr = Field(default=SecretStr(""))
    GEMMA_MODEL: str = Field(default="gemma-4-26b-a4b-it")
    GEMMA_TIMEOUT_SECONDS: float = Field(default=35.0, ge=2.0, le=90.0)
    GEMMA_MAX_RETRIES: int = Field(default=2, ge=0, le=4)

    DEMO_MODE: bool = Field(default=True)
    DEMO_ACCESS_TOKEN: SecretStr = Field(default=SecretStr(""))
    DEVICE_SHARED_TOKEN: SecretStr = Field(default=SecretStr(""))
    CORS_ORIGINS: str = Field(default="http://localhost:8501,http://127.0.0.1:8501")

    # Optional Google OAuth 2.0 credentials for Google Tasks & Google Calendar sync (Section 12)
    GOOGLE_CLIENT_ID: str = Field(default="")
    GOOGLE_CLIENT_SECRET: SecretStr = Field(default=SecretStr(""))
    GOOGLE_REDIRECT_URI: str = Field(
        default="http://localhost:8000/api/v1/integrations/google/callback"
    )

    # Deterministic XP configuration (Reward Engine)
    XP_SESSION_START: int = Field(default=10, ge=1, le=100)
    XP_MICROTASK_COMPLETE: int = Field(default=20, ge=1, le=200)
    XP_VOLUNTARY_CONTINUITY: int = Field(default=10, ge=1, le=100)
    XP_PER_LEVEL: int = Field(default=100, ge=20, le=10000)

    # Policy Engine safeguards
    MIN_INTERVENTION_COOLDOWN_SECONDS: int = Field(default=15, ge=0, le=3600)
    MAX_RECENT_IDENTICAL_HOOKS: int = Field(default=3, ge=1, le=10)

    @field_validator("GEMMA_MODEL")
    @classmethod
    def validate_gemma_only(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned.lower().startswith("gemma-"):
            raise ValueError(
                "FOXUS requires an open Google Gemma model (e.g. 'gemma-4-26b-a4b-it'). "
                "Proprietary Gemini model names are forbidden by architectural policy."
            )
        return cleaned

    @property
    def sqlalchemy_database_uri(self) -> str:
        return normalize_database_url(self.DATABASE_URL.get_secret_value())

    @property
    def has_gemini_api_key(self) -> bool:
        return bool(self.GEMINI_API_KEY.get_secret_value().strip())

    @property
    def cors_origins_list(self) -> list[str]:
        if not self.CORS_ORIGINS.strip():
            return []
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",") if origin.strip()]


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
