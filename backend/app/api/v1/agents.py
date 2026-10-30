from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agents.orchestrator import FoxusOrchestrator
from app.api.deps import get_current_demo_user, get_current_user_preferences
from app.core.database import get_db
from app.core.security import verify_demo_write_access
from app.models.entities import AgentTrace, Task, User, UserPreference
from app.schemas.agents import (
    AgentTraceStepRead,
    AgentTraceTimelineResponse,
    GenerateMissionRequest,
    GenerateMissionResponse,
    PressureEvaluationRequest,
    PressureEvaluationResponse,
)

router = APIRouter(prefix="/agents", tags=["Agents & Gemma 4"])


@router.post(
    "/generate-mission",
    response_model=GenerateMissionResponse,
    summary="Execute FOXUS multi-agent pipeline (Context -> Learning -> Microtask -> Hook -> Policy)",
)
def generate_mission(
    payload: GenerateMissionRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    preferences: UserPreference = Depends(get_current_user_preferences),
    db: Session = Depends(get_db),
) -> GenerateMissionResponse:
    task = db.scalar(select(Task).where(Task.id == payload.task_id, Task.user_id == user.id))
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found.")

    orchestrator = FoxusOrchestrator(db=db)
    return orchestrator.generate_mission(
        user=user,
        task=task,
        preferences=preferences,
        request=payload,
    )


@router.post(
    "/evaluate-pressure",
    response_model=PressureEvaluationResponse,
    summary="Evaluate proactive Duolingo-style interruption based on urgency, pressure consent, and Learning Agent",
)
def evaluate_pressure(
    payload: PressureEvaluationRequest,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    preferences: UserPreference = Depends(get_current_user_preferences),
    db: Session = Depends(get_db),
) -> PressureEvaluationResponse:
    orchestrator = FoxusOrchestrator(db=db)
    return orchestrator.evaluate_proactive_pressure(
        user=user,
        preferences=preferences,
        request=payload,
    )


@router.get(
    "/traces",
    response_model=list[AgentTraceStepRead],
    summary="List recent multi-agent execution trace steps for Agent Debug / Dashboard",
)
def list_recent_traces(
    limit: int = 40,
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> list[AgentTrace]:
    bounded = max(1, min(200, limit))
    stmt = (
        select(AgentTrace)
        .where(AgentTrace.user_id == user.id)
        .order_by(AgentTrace.created_at.desc())
        .limit(bounded)
    )
    return list(db.scalars(stmt).all())


@router.get(
    "/traces/{trace_id}",
    response_model=AgentTraceTimelineResponse,
    summary="Inspect full auditable trace timeline for a mission generation run",
)
def get_trace_timeline(
    trace_id: str,
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> AgentTraceTimelineResponse:
    stmt = (
        select(AgentTrace)
        .where(AgentTrace.trace_id == trace_id, AgentTrace.user_id == user.id)
        .order_by(AgentTrace.created_at.asc())
    )
    steps = list(db.scalars(stmt).all())
    if not steps:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Trace ID not found.")

    models = sorted(
        {
            s.model_name
            for s in steps
            if s.model_name
            and s.model_name not in ("deterministic_rules", "statistical_python")
        }
    )
    used_real_gemma = any(
        (s.model_name or "").lower().startswith("gemma-") and s.status == "ok"
        for s in steps
        if s.agent_name in ("microtask_agent", "hook_agent")
    )
    orchestrator_step = next((s for s in steps if s.agent_name == "orchestrator"), None)
    total_ms = (
        int(orchestrator_step.duration_ms or 0)
        if orchestrator_step is not None
        else sum(int(s.duration_ms or 0) for s in steps)
    )

    return AgentTraceTimelineResponse(
        trace_id=trace_id,
        total_duration_ms=total_ms,
        used_real_gemma=used_real_gemma,
        models_invoked=models,
        steps=[AgentTraceStepRead.model_validate(s) for s in steps],
    )
