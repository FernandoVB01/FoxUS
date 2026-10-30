from __future__ import annotations

from typing import Any
from fastapi import APIRouter

from app.core.config import get_settings
from app.core.database import check_database_health

router = APIRouter(tags=["System"])


@router.get(
    "/health",
    summary="Health check with real PostgreSQL SELECT 1 probe",
)
def get_health() -> dict[str, Any]:
    settings = get_settings()
    db_health = check_database_health()
    return {
        "status": "ok" if db_health.get("connected") else "degraded",
        "service": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "environment": settings.APP_ENV,
        "database": db_health,
        "ai": {
            "provider": "google-genai (Gemini API)",
            "configured_model": settings.GEMMA_MODEL,
            "api_key_configured": settings.has_gemini_api_key,
            "mode": "real_gemma" if settings.has_gemini_api_key else "deterministic_fallback",
        },
    }


@router.get(
    "/config/public",
    summary="Public non-sensitive configuration for Android APK and Dashboard",
)
def get_public_config() -> dict[str, Any]:
    settings = get_settings()
    return {
        "app_name": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "demo_mode": settings.DEMO_MODE,
        "gemma_model": settings.GEMMA_MODEL,
        "gemma_live_enabled": settings.has_gemini_api_key,
        "interactive_modes": [
            {
                "id": "challenge",
                "visual_name": "Desafío Relámpago",
                "description": "Convierte una tarea intimidante en un reto concreto de pocos minutos.",
            },
            {
                "id": "fox_bzz",
                "visual_name": "La Mosca Traviesa",
                "description": "Intervención juguetona y ligera dentro de FOXUS para romper la inercia.",
            },
        ],
        "xp_rules": {
            "session_start": settings.XP_SESSION_START,
            "microtask_complete": settings.XP_MICROTASK_COMPLETE,
            "voluntary_continuity": settings.XP_VOLUNTARY_CONTINUITY,
            "xp_per_level": settings.XP_PER_LEVEL,
        },
    }
