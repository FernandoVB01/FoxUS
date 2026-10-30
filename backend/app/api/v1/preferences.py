from __future__ import annotations

from datetime import UTC, datetime
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import get_current_user_preferences
from app.core.database import get_db
from app.core.security import verify_demo_write_access
from app.models.entities import UserPreference
from app.schemas.domain import UserPreferenceRead, UserPreferenceUpdate

router = APIRouter(prefix="/preferences", tags=["Preferences"])


@router.get(
    "",
    response_model=UserPreferenceRead,
    summary="Get user intervention, audio, music, and quiet-hours preferences",
)
def get_preferences(
    prefs: UserPreference = Depends(get_current_user_preferences),
) -> UserPreference:
    return prefs


@router.patch(
    "",
    response_model=UserPreferenceRead,
    summary="Update user preferences",
)
def update_preferences(
    payload: UserPreferenceUpdate,
    _auth: str = Depends(verify_demo_write_access),
    prefs: UserPreference = Depends(get_current_user_preferences),
    db: Session = Depends(get_db),
) -> UserPreference:
    NON_NULLABLE_FIELDS = {
        "intervention_level",
        "preferred_hook",
        "audio_enabled",
        "music_enabled",
        "timezone",
    }
    updates = payload.model_dump(exclude_unset=True)
    for field_name, val in updates.items():
        if val is None and field_name in NON_NULLABLE_FIELDS:
            continue
        setattr(prefs, field_name, val)
    prefs.updated_at = datetime.now(UTC)
    db.commit()
    db.refresh(prefs)
    return prefs
