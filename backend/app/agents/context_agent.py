from __future__ import annotations

import time
import uuid
from datetime import UTC, datetime
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.agents.contracts import (
    CONTEXT_AGENT_SYSTEM_PROMPT,
    AgentExecutionStep,
    GemmaContextInterpretation,
)
from app.integrations.calendar_provider import CalendarProvider, DatabaseSyncedCalendarProvider
from app.integrations.tasks_provider import ExternalTaskRecord, LocalTasksProvider, TasksProvider
from app.models.entities import FocusSession, Hook, Microtask, Task, UserPreference
from app.schemas.agents import CalendarEventItem, TaskContext
from app.services.gemma_client import GemmaClient
from app.services.timing import BUCKET_LABELS, PHASE_LABELS, compute_time_context


class ContextAgent:
    """
    Context Agent (Section 4.2).
    Executes deterministic internal tools (`get_user_tasks`, `get_calendar_events`,
    `get_user_preferences`, `get_recent_sessions`) including synced Google Calendar
    and Google Tasks data, and optionally uses Gemma 4 to synthesize an empathetic summary.
    """

    def __init__(
        self,
        db: Session,
        gemma_client: GemmaClient,
        tasks_provider: TasksProvider | None = None,
        calendar_provider: CalendarProvider | None = None,
    ) -> None:
        self._db = db
        self._gemma = gemma_client
        self._tasks_provider = tasks_provider or LocalTasksProvider(db)
        self._calendar_provider = calendar_provider or DatabaseSyncedCalendarProvider(db)

    # Required internal tools (Section 4.2)
    def get_user_tasks(self, user_id: uuid.UUID) -> list[ExternalTaskRecord]:
        return self._tasks_provider.list_pending_tasks(user_id)

    def get_calendar_events(self, user_id: uuid.UUID) -> list[CalendarEventItem]:
        return self._calendar_provider.get_upcoming_events(user_id, horizon_hours=24)

    def get_user_preferences(self, user_id: uuid.UUID) -> UserPreference | None:
        return self._db.scalar(select(UserPreference).where(UserPreference.user_id == user_id))

    def get_recent_sessions(self, user_id: uuid.UUID, limit: int = 10) -> list[FocusSession]:
        stmt = (
            select(FocusSession)
            .where(FocusSession.user_id == user_id)
            .order_by(FocusSession.created_at.desc())
            .limit(limit)
        )
        return list(self._db.scalars(stmt).all())

    def _count_recent_rejections_for_task(self, user_id: uuid.UUID, task_id: uuid.UUID) -> int:
        stmt = (
            select(func.count(Hook.id))
            .join(Microtask, Hook.microtask_id == Microtask.id)
            .where(
                Hook.user_id == user_id,
                Microtask.task_id == task_id,
                Hook.status.in_(("rejected", "postponed", "silenced")),
            )
        )
        return int(self._db.scalar(stmt) or 0)

    @staticmethod
    def _compute_urgency(hours_until_deadline: float | None, needs_pressure: bool) -> str:
        if hours_until_deadline is not None:
            if hours_until_deadline <= 6 or (needs_pressure and hours_until_deadline <= 18):
                return "critical"
            if hours_until_deadline <= 24:
                return "high"
            if hours_until_deadline <= 72:
                return "high" if needs_pressure else "medium"
        return "high" if needs_pressure else "medium"

    def analyze(
        self,
        *,
        user_id: uuid.UUID,
        task: Task,
        preferences: UserPreference,
        available_minutes: int,
        perceived_difficulty: int | None,
    ) -> AgentExecutionStep[TaskContext]:
        start_ns = time.perf_counter_ns()
        now = datetime.now(UTC)

        pending_tasks = self.get_user_tasks(user_id)
        calendar_events = self.get_calendar_events(user_id)
        _ = self.get_user_preferences(user_id)
        recent_sessions = self.get_recent_sessions(user_id)
        rejections = self._count_recent_rejections_for_task(user_id, task.id)

        hours_left: float | None = None
        if task.deadline is not None:
            dl = task.deadline if task.deadline.tzinfo else task.deadline.replace(tzinfo=UTC)
            hours_left = round((dl - now).total_seconds() / 3600.0, 2)

        tctx = compute_time_context(
            now_utc=now,
            timezone_name=getattr(preferences, "timezone", None),
            deadline=task.deadline,
            events=calendar_events,
            pending_deadlines=[t.deadline for t in pending_tasks],
        )

        needs_pressure = bool(getattr(task, "needs_pressure", False))
        urgency = self._compute_urgency(hours_left, needs_pressure)

        next_event_clause = ""
        if tctx.in_event_now:
            available_minutes = min(available_minutes, 3)
            next_event_clause = f" Estás en '{tctx.next_event_title}'."
        elif tctx.minutes_until_next_event is not None and 5 <= tctx.minutes_until_next_event <= 90:
            available_minutes = min(available_minutes, max(3, tctx.minutes_until_next_event - 5))
            next_event_clause = (
                f" Próximo evento '{tctx.next_event_title}' en {tctx.minutes_until_next_event} min."
            )

        deadline_clause = (
            f"Entrega en {hours_left:.1f} h."
            if hours_left is not None and hours_left > 0
            else ("Fecha límite vencida." if hours_left is not None else "Sin fecha límite fija.")
        )
        pressure_clause = " Modo presión voluntario activo." if needs_pressure else ""
        load_clause = f" {tctx.load_next_72h} entregas en 72 h." if tctx.load_next_72h >= 2 else ""
        deterministic_summary = (
            f"Tarea '{task.title}' ({PHASE_LABELS[tctx.urgency_phase]}, "
            f"{BUCKET_LABELS[tctx.time_bucket]}). {deadline_clause}{pressure_clause}"
            f"{next_event_clause}{load_clause} Ventana: {available_minutes} min."
        )

        summary_text = deterministic_summary
        gen_source = "deterministic_rules"
        model_used: str | None = None
        step_status = "ok"

        needs_llm_interpretation = len((task.description or "").strip()) >= 140
        if self._gemma.is_configured and needs_llm_interpretation:
            gemma_res = self._gemma.generate_structured(
                system_prompt=CONTEXT_AGENT_SYSTEM_PROMPT,
                user_payload={
                    "task_title": task.title,
                    "task_description": task.description,
                    "hours_until_deadline": hours_left,
                    "urgency_phase": tctx.urgency_phase,
                    "local_time_bucket": tctx.time_bucket,
                    "needs_pressure": needs_pressure,
                    "urgency_level": urgency,
                    "available_minutes": available_minutes,
                    "perceived_difficulty": perceived_difficulty,
                    "recent_rejections_for_task": rejections,
                    "pending_tasks_count": len(pending_tasks),
                    "upcoming_events_count": len(calendar_events),
                    "recent_sessions_count": len(recent_sessions),
                },
                schema_model=GemmaContextInterpretation,
                temperature=0.25,
            )
            if gemma_res.ok and gemma_res.parsed is not None:
                summary_text = gemma_res.parsed.contextual_summary
                gen_source = "gemma"
                model_used = gemma_res.model_used
            else:
                step_status = "fallback"

        context_out = TaskContext(
            user_id=user_id,
            task_id=task.id,
            task_title=task.title,
            task_description=task.description,
            deadline=task.deadline,
            hours_until_deadline=hours_left,
            needs_pressure=needs_pressure,
            urgency_level=urgency,  # type: ignore[arg-type]
            available_minutes=available_minutes,
            perceived_difficulty=perceived_difficulty,
            recent_rejections_for_task=rejections,
            recent_sessions_count=len(recent_sessions),
            upcoming_events=calendar_events,
            contextual_summary=summary_text,
            generation_source=gen_source,  # type: ignore[arg-type]
            model_name=model_used,
            urgency_phase=tctx.urgency_phase,
            time_bucket=tctx.time_bucket,
            local_time_label=tctx.local_now.strftime("%H:%M"),
            minutes_until_next_event=tctx.minutes_until_next_event,
            next_event_title=tctx.next_event_title,
            load_next_72h=tctx.load_next_72h,
        )

        elapsed_ms = max(1, int((time.perf_counter_ns() - start_ns) // 1_000_000))
        return AgentExecutionStep(
            agent_name="context_agent",
            output=context_out,
            model_name=model_used,
            duration_ms=elapsed_ms,
            status=step_status,  # type: ignore[arg-type]
            input_summary={
                "task_id": str(task.id),
                "needs_pressure": needs_pressure,
                "available_minutes": available_minutes,
                "perceived_difficulty": perceived_difficulty,
            },
            output_summary={
                "urgency_level": urgency,
                "urgency_phase": tctx.urgency_phase,
                "time_bucket": tctx.time_bucket,
                "recent_rejections_for_task": rejections,
                "upcoming_events_count": len(calendar_events),
                "load_next_72h": tctx.load_next_72h,
                "generation_source": gen_source,
            },
        )
