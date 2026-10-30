from __future__ import annotations

from typing import Any
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.database import get_db
from app.core.security import verify_demo_write_access
from app.repositories.seed import reset_demo_state

router = APIRouter(prefix="/demo", tags=["Demo"])


@router.post(
    "/reset",
    summary="Reset demo user tasks, traces, sessions, and XP (restricted to DEMO_MODE=true)",
)
def reset_demo(
    _auth: str = Depends(verify_demo_write_access),
    db: Session = Depends(get_db),
) -> dict[str, Any]:
    settings = get_settings()
    if not settings.DEMO_MODE:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Demo reset is disabled when DEMO_MODE is false.",
        )

    user = reset_demo_state(db)
    return {
        "reset": True,
        "demo_user_id": str(user.id),
        "display_name": user.display_name,
    }
