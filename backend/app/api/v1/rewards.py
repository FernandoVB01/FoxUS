from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_demo_user
from app.core.database import get_db
from app.models.entities import RewardTransaction, User, UserHookStat
from app.schemas.domain import HookStatRead, RewardProfileResponse, RewardTransactionRead
from app.services.reward_engine import RewardEngine

router = APIRouter(prefix="/rewards", tags=["Rewards & Gamification"])


@router.get(
    "/profile",
    response_model=RewardProfileResponse,
    summary="Get deterministic XP, level progression, achievements, and hook stats",
)
def get_reward_profile(
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> RewardProfileResponse:
    engine = RewardEngine()
    total_xp = engine.get_user_total_xp(db, user.id)
    lvl = engine.compute_level_status(total_xp)
    started_count, completed_count, badges = engine.build_achievements(
        db, user_id=user.id, total_xp=total_xp
    )
    hook_stats = list(
        db.scalars(
            select(UserHookStat)
            .where(UserHookStat.user_id == user.id)
            .order_by(UserHookStat.hook_type.asc())
        ).all()
    )
    return RewardProfileResponse(
        user_id=user.id,
        display_name=user.display_name,
        total_xp=lvl.total_xp,
        level=lvl.level,
        xp_in_current_level=lvl.xp_in_current_level,
        xp_for_next_level=lvl.xp_for_next_level,
        progress_ratio=lvl.progress_ratio,
        completed_microtasks_count=completed_count,
        started_sessions_count=started_count,
        achievements=badges,
        hook_stats=[HookStatRead.model_validate(s) for s in hook_stats],
    )


@router.get(
    "/history",
    response_model=list[RewardTransactionRead],
    summary="List recent deterministic XP transactions",
)
def get_reward_history(
    limit: int = 40,
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> list[RewardTransaction]:
    bounded = max(1, min(150, limit))
    stmt = (
        select(RewardTransaction)
        .where(RewardTransaction.user_id == user.id)
        .order_by(RewardTransaction.created_at.desc())
        .limit(bounded)
    )
    return list(db.scalars(stmt).all())
