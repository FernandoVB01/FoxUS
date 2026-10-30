from __future__ import annotations

from fastapi import Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.entities import User, UserPreference
from app.repositories.seed import ensure_demo_user_and_seed


def get_current_demo_user(db: Session = Depends(get_db)) -> User:
    """
    Resolve the controlled MVP demo user (Section 9).
    Prepared to swap cleanly with Google OAuth 2.0 native token resolution.
    """
    return ensure_demo_user_and_seed(db)


def get_current_user_preferences(
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> UserPreference:
    prefs = db.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    if prefs is None:
        ensure_demo_user_and_seed(db)
        prefs = db.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    assert prefs is not None
    return prefs
