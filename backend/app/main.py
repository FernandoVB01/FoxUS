from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.exc import OperationalError, TimeoutError as DatabaseTimeoutError

from app.api.v1.router import api_v1_router
from app.core.config import get_settings


def create_app() -> FastAPI:
    settings = get_settings()

    application = FastAPI(
        title=settings.APP_NAME,
        version=settings.APP_VERSION,
        description=(
            "FOXUS — AI-Powered Anti-Procrastination Companion Backend.\n\n"
            "Multi-Agent Architecture (Orchestrator + Context, Microtask, Hook, Learning Agents) "
            "powered by Google Gemma 4 (`gemma-4-26b-a4b-it`) via `google-genai`, "
            "deterministic Policy & Reward Engines, PostgreSQL, and ESP32 Smartwatch protocol."
        ),
        openapi_url=f"{settings.API_V1_PREFIX}/openapi.json",
        docs_url="/docs",
        redoc_url="/redoc",
    )

    if settings.cors_origins_list:
        application.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origins_list,
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )

    async def database_unavailable(_request, _error):
        return JSONResponse(status_code=503, content={
            "code": "database_unavailable",
            "detail": "No pudimos conectar con la base de datos. Reintenta cuando vuelva la conexión.",
        }, headers={"Retry-After": "15"})

    application.add_exception_handler(OperationalError, database_unavailable)
    application.add_exception_handler(DatabaseTimeoutError, database_unavailable)
    application.include_router(api_v1_router, prefix=settings.API_V1_PREFIX)
    return application


app = create_app()
