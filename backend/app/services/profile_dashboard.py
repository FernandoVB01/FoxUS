"""Dashboard values derived from recorded sessions, never inferred mental attention."""
from datetime import UTC, datetime, timedelta
from collections import defaultdict
from sqlalchemy import select
from app.models.entities import FocusSession, Microtask, SessionEvent


def dashboard(db, user_id):
    since = datetime.now(UTC) - timedelta(days=30)
    sessions = list(db.scalars(select(FocusSession).where(
        FocusSession.user_id == user_id, FocusSession.created_at >= since,
        FocusSession.status.in_(("completed", "stopped")))))
    ids = [s.id for s in sessions]
    events = list(db.scalars(select(SessionEvent).where(SessionEvent.session_id.in_(ids)))) if ids else []
    durations = {}
    measured = defaultdict(lambda: [0, 0])
    for event in events:
        if event.event_type == "MICROTASK_COMPLETED_SELF_REPORTED":
            seconds = (event.event_metadata or {}).get("actual_duration_seconds")
            if isinstance(seconds, int):
                durations[event.session_id] = max(0, min(seconds, 7200))
    for event in events:
        if event.event_type == "MOMENT_UNITS":
            data = event.event_metadata or {}
            seconds = durations.get(event.session_id, 0)
            if seconds > 0:
                measured[data["unit"]][0] += data["count"]
                measured[data["unit"]][1] += seconds
    completed = [s for s in sessions if s.status == "completed"]
    return dict(period_days=30, focus_minutes=sum(durations.values()) // 60,
                advances=len(completed), activities=len({db.get(Microtask, s.microtask_id).task_id for s in completed}),
                completion_rate=round(100 * len(completed) / len(sessions)) if sessions else None,
                verified=len({e.session_id for e in events if e.event_type == "EVIDENCE_VERIFIED"}),
                speeds=[dict(unit=unit, per_hour=round(n * 3600 / secs, 1))
                        for unit, (n, secs) in sorted(measured.items())])


def continuation_suggestion(title):
    # A proposal, not a claim about units already completed or remaining.
    lower = title.lower()
    if "diapositiv" in lower or "slide" in lower:
        return "¿Puedes hacer 6 diapositivas más?", "Hacer 6 diapositivas más"
    if "estudi" in lower or "leer" in lower:
        return "¿Puedes avanzar una sección más?", "Avanzar una sección más"
    return "¿Puedes dar otro paso en esta actividad?", "Avanzar un paso más"
