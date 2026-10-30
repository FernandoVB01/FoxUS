from __future__ import annotations

from fastapi import APIRouter

from app.api.v1 import (
    agents,
    demo,
    devices,
    integrations,
    preferences,
    rewards,
    sessions,
    system,
    tasks,
)

api_v1_router = APIRouter()
api_v1_router.include_router(system.router)
api_v1_router.include_router(tasks.router)
api_v1_router.include_router(agents.router)
api_v1_router.include_router(sessions.router)
api_v1_router.include_router(rewards.router)
api_v1_router.include_router(preferences.router)
api_v1_router.include_router(devices.router)
api_v1_router.include_router(integrations.router)
api_v1_router.include_router(demo.router)
