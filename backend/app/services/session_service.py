from __future__ import annotations

import uuid
from datetime import UTC, datetime
from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agents.learning_agent import LearningAgent
from app.models.entities import FocusSession, Hook, Microtask, SessionEvent, Task, User
from app.schemas.sessions import HookDismissResponse, SessionActionResponse, SessionRead
from app.services.reward_engine import RewardEngine


class SessionLifecycleService:
    """
    Single source of truth for starting, completing, and stopping/postponing focus sessions
    across Android APK, ESP32 smartwatch buttons, and hardware simulator.
    """

    def __init__(self, db: Session, reward_engine: RewardEngine | None = None) -> None:
        self._db = db
        self._rewards = reward_engine or RewardEngine()
        self._learning = LearningAgent(db=db)

    def start_session(
        self,
        *,
        user: User,
        microtask_id: uuid.UUID,
        hook_id: uuid.UUID | None,
        source: str,
        idempotency_key: str | None = None,
    ) -> SessionActionResponse:
        microtask = self._db.get(Microtask, microtask_id)
        if microtask is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND, detail="Microtask not found."
            )
        task = self._db.get(Task, microtask.task_id)
        if task is None or task.user_id != user.id:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND, detail="Parent task not found for user."
            )

        # Idempotent reuse if an active started session already exists for this microtask
        existing_session = self._db.scalar(
            select(FocusSession)
            .where(
                FocusSession.user_id == user.id,
                FocusSession.microtask_id == microtask_id,
                FocusSession.status == "started",
            )
            .order_by(FocusSession.created_at.desc())
        )
        now = datetime.now(UTC)
        hook_row = self._db.get(Hook, hook_id) if hook_id else None

        if existing_session is not None:
            session_row = existing_session
        else:
            session_row = FocusSession(
                user_id=user.id,
                microtask_id=microtask_id,
                hook_id=hook_row.id if hook_row else None,
                started_at=now,
                status="started",
            )
            self._db.add(session_row)
            self._db.flush()

            if task.status == "pending":
                task.status = "in_progress"

            if hook_row is not None and hook_row.status not in ("accepted",):
                hook_row.status = "accepted"
                self._learning.record_outcome(
                    user_id=user.id, hook_type=hook_row.hook_type, outcome="accepted"
                )
                self._learning.record_outcome(
                    user_id=user.id, hook_type=hook_row.hook_type, outcome="started"
                )
            else:
                self._learning.record_outcome(
                    user_id=user.id, hook_type="challenge", outcome="started"
                )

            self._db.add(
                SessionEvent(
                    session_id=session_row.id,
                    event_type="SESSION_STARTED",
                    source=source,
                    event_metadata={"microtask_id": str(microtask_id)},
                )
            )

        grant = self._rewards.grant_session_start_xp(
            self._db,
            user_id=user.id,
            session_id=session_row.id,
            microtask_id=microtask_id,
            custom_key=idempotency_key,
        )
        self._db.commit()
        self._db.refresh(session_row)

        total_xp = self._rewards.get_user_total_xp(self._db, user.id)
        lvl = self._rewards.compute_level_status(total_xp)
        return SessionActionResponse(
            session=SessionRead.model_validate(session_row),
            rewards_granted=[grant],
            total_xp=lvl.total_xp,
            current_level=lvl.level,
            celebrate=False,
        )

    def complete_session(
        self,
        *,
        user: User,
        session_id: uuid.UUID,
        source: str,
        voluntary_continuity: bool = False,
        actual_duration_seconds: int | None = None,
        mark_parent_task_done: bool = False,
        idempotency_key: str | None = None,
    ) -> SessionActionResponse:
        session_row = self._db.scalar(
            select(FocusSession).where(
                FocusSession.id == session_id, FocusSession.user_id == user.id
            )
        )
        if session_row is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND, detail="Session not found."
            )

        now = datetime.now(UTC)
        was_already_completed = session_row.status == "completed"

        if not was_already_completed:
            session_row.status = "completed"
            session_row.ended_at = now
            # Explicitly mark completion as self-reported (Section 13)
            session_row.completion_source = f"{source}_self_reported"

            hook_row = self._db.get(Hook, session_row.hook_id) if session_row.hook_id else None
            hook_type = hook_row.hook_type if hook_row else "challenge"
            self._learning.record_outcome(
                user_id=user.id, hook_type=hook_type, outcome="completed"
            )

            if mark_parent_task_done:
                microtask = self._db.get(Microtask, session_row.microtask_id)
                if microtask is not None:
                    parent = self._db.get(Task, microtask.task_id)
                    # Only mark manual local tasks done if explicitly requested; never auto-complete external tasks (Section 12)
                    if parent is not None and parent.source == "manual":
                        parent.status = "completed"

            self._db.add(
                SessionEvent(
                    session_id=session_row.id,
                    event_type="MICROTASK_COMPLETED_SELF_REPORTED",
                    source=source,
                    event_metadata={
                        "voluntary_continuity": voluntary_continuity,
                        "actual_duration_seconds": actual_duration_seconds,
                    },
                )
            )

        grants = self._rewards.grant_completion_xp(
            self._db,
            user_id=user.id,
            session_id=session_row.id,
            microtask_id=session_row.microtask_id,
            voluntary_continuity=voluntary_continuity,
            custom_key=idempotency_key,
        )
        self._db.commit()
        self._db.refresh(session_row)

        total_xp = self._rewards.get_user_total_xp(self._db, user.id)
        lvl = self._rewards.compute_level_status(total_xp)
        any_new_xp = any(not g.already_granted and g.xp_awarded > 0 for g in grants)
        return SessionActionResponse(
            session=SessionRead.model_validate(session_row),
            rewards_granted=grants,
            total_xp=lvl.total_xp,
            current_level=lvl.level,
            celebrate=any_new_xp,
        )

    def stop_session(
        self,
        *,
        user: User,
        session_id: uuid.UUID,
        source: str,
        reason: str,
    ) -> SessionActionResponse:
        session_row = self._db.scalar(
            select(FocusSession).where(
                FocusSession.id == session_id, FocusSession.user_id == user.id
            )
        )
        if session_row is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND, detail="Session not found."
            )

        if session_row.status == "started":
            session_row.status = "stopped"
            session_row.ended_at = datetime.now(UTC)
            session_row.completion_source = reason

            hook_row = self._db.get(Hook, session_row.hook_id) if session_row.hook_id else None
            if hook_row is not None and reason in ("postponed", "silenced", "disturbance_reported"):
                hook_row.status = "silenced" if reason == "silenced" else "postponed"
                self._learning.record_outcome(
                    user_id=user.id, hook_type=hook_row.hook_type, outcome="rejected"
                )

            self._db.add(
                SessionEvent(
                    session_id=session_row.id,
                    event_type="SESSION_STOPPED",
                    source=source,
                    event_metadata={"reason": reason},
                )
            )
            self._db.commit()
            self._db.refresh(session_row)

        total_xp = self._rewards.get_user_total_xp(self._db, user.id)
        lvl = self._rewards.compute_level_status(total_xp)
        return SessionActionResponse(
            session=SessionRead.model_validate(session_row),
            rewards_granted=[],
            total_xp=lvl.total_xp,
            current_level=lvl.level,
            celebrate=False,
        )

    def dismiss_hook(
        self,
        *,
        user: User,
        hook_id: uuid.UUID,
        source: str,
        reason: str,
    ) -> HookDismissResponse:
        hook_row = self._db.scalar(
            select(Hook).where(Hook.id == hook_id, Hook.user_id == user.id)
        )
        if hook_row is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND, detail="Hook not found."
            )

        event_type = f"HOOK_{reason.upper()}"
        if hook_row.status not in ("postponed", "silenced", "rejected", "accepted"):
            hook_row.status = reason
            self._learning.record_outcome(
                user_id=user.id, hook_type=hook_row.hook_type, outcome="rejected"
            )
            self._db.add(
                SessionEvent(
                    session_id=None,
                    event_type=event_type,
                    source=source,
                    event_metadata={
                        "hook_id": str(hook_row.id),
                        "hook_type": hook_row.hook_type,
                        "microtask_id": str(hook_row.microtask_id),
                        "reason": reason,
                    },
                )
            )
            self._db.commit()
            self._db.refresh(hook_row)

        return HookDismissResponse(
            hook_id=hook_row.id,
            hook_type=hook_row.hook_type,
            status=hook_row.status,
            recorded_event_type=event_type,
        )
