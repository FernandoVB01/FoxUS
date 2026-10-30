from __future__ import annotations

import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agents.microtask_agent import normalize_bullet_steps
from app.agents.referee_agent import RefereeAgent
from app.api.deps import get_current_demo_user
from app.core.database import get_db
from app.core.security import verify_demo_write_access
from app.models.entities import AgentTrace, FocusSession, Microtask, SessionEvent, Task, User
from app.schemas.sessions import (
    HookDismissRequest,
    HookDismissResponse,
    SessionActionResponse,
    SessionCompleteRequest,
    SessionEvidenceVerifyRequest,
    SessionEvidenceVerifyResponse,
    SessionRead,
    SessionStartRequest,
    SessionStopRequest,
)
from app.services.gemma_client import GemmaClient
from app.services.reward_engine import RewardEngine
from app.services.session_service import SessionLifecycleService

router = APIRouter(prefix="/sessions", tags=["Sessions"])


@router.post(
    "/start",
    response_model=SessionActionResponse,
    summary="Start a microtask focus session and grant deterministic initiation XP",
)
def start_session(
    payload: SessionStartRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> SessionActionResponse:
    svc = SessionLifecycleService(db=db)
    return svc.start_session(
        user=user,
        microtask_id=payload.microtask_id,
        hook_id=payload.hook_id,
        source=payload.source,
        idempotency_key=payload.idempotency_key,
    )


@router.post(
    "/{session_id}/complete",
    response_model=SessionActionResponse,
    summary="Complete a session (self-reported) and grant deterministic completion XP",
)
def complete_session(
    session_id: uuid.UUID,
    payload: SessionCompleteRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> SessionActionResponse:
    svc = SessionLifecycleService(db=db)
    return svc.complete_session(
        user=user,
        session_id=session_id,
        source=payload.source,
        voluntary_continuity=payload.voluntary_continuity,
        actual_duration_seconds=payload.actual_duration_seconds,
        mark_parent_task_done=payload.mark_parent_task_done,
        idempotency_key=payload.idempotency_key,
    )


@router.post(
    "/{session_id}/stop",
    response_model=SessionActionResponse,
    summary="Stop or postpone an active focus session",
)
def stop_session(
    session_id: uuid.UUID,
    payload: SessionStopRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> SessionActionResponse:
    svc = SessionLifecycleService(db=db)
    return svc.stop_session(
        user=user,
        session_id=session_id,
        source=payload.source,
        reason=payload.reason,
    )


@router.post(
    "/hooks/{hook_id}/dismiss",
    response_model=HookDismissResponse,
    summary="Postpone or silence a hook proposal (e.g. FOX BZZ or Challenge) before session start",
)
def dismiss_hook(
    hook_id: uuid.UUID,
    payload: HookDismissRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> HookDismissResponse:
    svc = SessionLifecycleService(db=db)
    return svc.dismiss_hook(
        user=user,
        hook_id=hook_id,
        source=payload.source,
        reason=payload.reason,
    )


@router.post(
    "/{session_id}/verify-evidence",
    response_model=SessionEvidenceVerifyResponse,
    summary="Verify end-of-challenge photo or voice evidence with RefereeAgent (Gemma 4) and award bonus XP",
)
def verify_session_evidence(
    session_id: uuid.UUID,
    payload: SessionEvidenceVerifyRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> SessionEvidenceVerifyResponse:
    sess = db.scalar(
        select(FocusSession).where(
            FocusSession.id == session_id,
            FocusSession.user_id == user.id,
        )
    )
    if sess is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sesión no encontrada.")

    micro = db.get(Microtask, sess.microtask_id)
    parent_task = db.get(Task, micro.task_id) if micro is not None else None

    task_title = parent_task.title if parent_task is not None else "Actividad de enfoque"
    micro_title = micro.title if micro is not None else task_title
    micro_steps = (
        normalize_bullet_steps([], micro.description, micro.success_criteria)
        if micro is not None
        else [micro_title]
    )

    referee = RefereeAgent(GemmaClient())
    step = referee.verify_evidence(
        task_title=task_title,
        microtask_title=micro_title,
        microtask_steps=micro_steps,
        evidence_type=payload.evidence_type,
        image_base64=payload.image_base64,
        image_mime_type=payload.image_mime_type,
        voice_transcript=payload.voice_transcript,
        checked_steps_count=payload.checked_steps_count,
    )

    trace_id = f"referee-{uuid.uuid4().hex[:12]}"
    db.add(
        AgentTrace(
            trace_id=trace_id,
            user_id=user.id,
            agent_name=step.agent_name,
            model_name=step.model_name,
            duration_ms=step.duration_ms,
            status=step.status,
            input_summary=step.input_summary,
            output_summary=step.output_summary,
        )
    )

    reward_engine = RewardEngine()
    svc = SessionLifecycleService(db=db, reward_engine=reward_engine)
    session_res = svc.complete_session(
        user=user,
        session_id=session_id,
        source=payload.source,
        actual_duration_seconds=payload.actual_duration_seconds,
        idempotency_key=payload.idempotency_key,
    )

    bonus_xp = 0
    if step.output.verified:
        bonus_grant = reward_engine.grant_verified_evidence_xp(
            db,
            user_id=user.id,
            session_id=session_id,
            microtask_id=sess.microtask_id,
        )
        if not bonus_grant.already_granted:
            from app.models.entities import Hook, ChainLink
            from app.services.technique_advisor import TechniqueAdvisor
            hook = db.get(Hook, sess.hook_id) if sess.hook_id else None
            if hook:
                TechniqueAdvisor(db).record(user_id=user.id, technique=hook.technique,
                                             context_key=hook.context_key, outcome="verified")
            link = db.scalar(select(ChainLink).where(ChainLink.session_id == session_id))
            if link:
                link.verified = True
                link.evidence_type = payload.evidence_type
        bonus_xp = bonus_grant.xp_awarded
        session_res.rewards_granted.append(bonus_grant)
        total_xp = reward_engine.get_user_total_xp(db, user.id)
        lvl = reward_engine.compute_level_status(total_xp)
        session_res.total_xp = total_xp
        session_res.current_level = lvl.level

    db.add(
        SessionEvent(
            session_id=session_id,
            event_type="EVIDENCE_VERIFIED" if step.output.verified else "EVIDENCE_REVIEWED",
            source=payload.source,
            event_metadata={
                "evidence_type": payload.evidence_type,
                "verified": step.output.verified,
                "confidence_score": step.output.confidence_score,
                "model_name": step.output.model_name,
            },
        )
    )
    db.commit()

    return SessionEvidenceVerifyResponse(
        session_result=session_res,
        verdict=step.output,
        bonus_xp_awarded=bonus_xp,
        trace_id=trace_id,
    )


@router.get(
    "/history",
    response_model=list[SessionRead],
    summary="List recent focus sessions for the user",
)
def get_sessions_history(
    limit: int = 30,
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> list[FocusSession]:
    bounded = max(1, min(100, limit))
    stmt = (
        select(FocusSession)
        .where(FocusSession.user_id == user.id)
        .order_by(FocusSession.created_at.desc())
        .limit(bounded)
    )
    return list(db.scalars(stmt).all())
