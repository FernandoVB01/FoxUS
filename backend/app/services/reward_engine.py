from __future__ import annotations

import uuid
from dataclasses import dataclass
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.models.entities import FocusSession, RewardTransaction
from app.schemas.sessions import RewardGrantSummary


@dataclass(slots=True)
class LevelStatus:
    total_xp: int
    level: int
    xp_in_current_level: int
    xp_for_next_level: int
    progress_ratio: float


class RewardEngine:
    """
    Deterministic Reward Engine (Section 4.7 & Section 13).
    AI never decides XP amounts. Enforces idempotency and per-microtask caps.
    """

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()

    def compute_level_status(self, total_xp: int) -> LevelStatus:
        step = self._settings.XP_PER_LEVEL
        safe_xp = max(0, total_xp)
        level = (safe_xp // step) + 1
        xp_in_level = safe_xp % step
        ratio = round(xp_in_level / step, 4) if step > 0 else 0.0
        return LevelStatus(
            total_xp=safe_xp,
            level=level,
            xp_in_current_level=xp_in_level,
            xp_for_next_level=step,
            progress_ratio=ratio,
        )

    def get_user_total_xp(self, db: Session, user_id: uuid.UUID) -> int:
        val = db.scalar(
            select(func.coalesce(func.sum(RewardTransaction.xp_amount), 0)).where(
                RewardTransaction.user_id == user_id
            )
        )
        return int(val or 0)

    def _grant_once(
        self,
        db: Session,
        *,
        user_id: uuid.UUID,
        session_id: uuid.UUID | None,
        reward_type: str,
        xp_amount: int,
        idempotency_key: str,
    ) -> RewardGrantSummary:
        existing = db.scalar(
            select(RewardTransaction).where(
                RewardTransaction.idempotency_key == idempotency_key
            )
        )
        if existing is not None:
            return RewardGrantSummary(
                reward_type=existing.reward_type,
                xp_awarded=0,
                already_granted=True,
                idempotency_key=idempotency_key,
            )

        tx = RewardTransaction(
            user_id=user_id,
            session_id=session_id,
            reward_type=reward_type,
            xp_amount=xp_amount,
            idempotency_key=idempotency_key,
        )
        db.add(tx)
        db.flush()
        return RewardGrantSummary(
            reward_type=reward_type,
            xp_awarded=xp_amount,
            already_granted=False,
            idempotency_key=idempotency_key,
        )

    def grant_session_start_xp(
        self,
        db: Session,
        *,
        user_id: uuid.UUID,
        session_id: uuid.UUID,
        microtask_id: uuid.UUID,
        custom_key: str | None = None,
    ) -> RewardGrantSummary:
        # Cap start XP to once per microtask so repeated start/stop loops cannot farm XP
        key = custom_key or f"microtask:{microtask_id}:session_start"
        return self._grant_once(
            db,
            user_id=user_id,
            session_id=session_id,
            reward_type="session_start",
            xp_amount=self._settings.XP_SESSION_START,
            idempotency_key=key,
        )

    def grant_completion_xp(
        self,
        db: Session,
        *,
        user_id: uuid.UUID,
        session_id: uuid.UUID,
        microtask_id: uuid.UUID,
        voluntary_continuity: bool = False,
        custom_key: str | None = None,
    ) -> list[RewardGrantSummary]:
        grants: list[RewardGrantSummary] = []

        comp_key = custom_key or f"microtask:{microtask_id}:microtask_complete"
        grants.append(
            self._grant_once(
                db,
                user_id=user_id,
                session_id=session_id,
                reward_type="microtask_complete",
                xp_amount=self._settings.XP_MICROTASK_COMPLETE,
                idempotency_key=comp_key,
            )
        )

        if voluntary_continuity:
            cont_key = f"microtask:{microtask_id}:voluntary_continuity"
            grants.append(
                self._grant_once(
                    db,
                    user_id=user_id,
                    session_id=session_id,
                    reward_type="voluntary_continuity",
                    xp_amount=self._settings.XP_VOLUNTARY_CONTINUITY,
                    idempotency_key=cont_key,
                )
            )
        return grants

    def grant_verified_evidence_xp(
        self,
        db: Session,
        *,
        user_id: uuid.UUID,
        session_id: uuid.UUID,
        microtask_id: uuid.UUID,
        custom_key: str | None = None,
    ) -> RewardGrantSummary:
        ev_key = custom_key or f"microtask:{microtask_id}:verified_evidence"
        return self._grant_once(
            db,
            user_id=user_id,
            session_id=session_id,
            reward_type="verified_evidence",
            xp_amount=15,
            idempotency_key=ev_key,
        )

    def build_achievements(
        self,
        db: Session,
        *,
        user_id: uuid.UUID,
        total_xp: int,
    ) -> tuple[int, int, list[str]]:
        started_count = int(
            db.scalar(
                select(func.count(FocusSession.id)).where(FocusSession.user_id == user_id)
            )
            or 0
        )
        completed_count = int(
            db.scalar(
                select(func.count(FocusSession.id)).where(
                    FocusSession.user_id == user_id,
                    FocusSession.status == "completed",
                )
            )
            or 0
        )

        badges: list[str] = []
        if started_count >= 1:
            badges.append("Primer Impulso: Rompiste la inercia inicial")
        if completed_count >= 1:
            badges.append("Microacción Cumplida: Avance real registrado")
        if completed_count >= 5:
            badges.append("Racha Astuta: 5 microacciones superadas")
        if total_xp >= 100:
            badges.append("Zorro Nivel 2: 100+ XP acumulados")
        return started_count, completed_count, badges
