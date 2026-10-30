from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Literal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from app.schemas.agents import CalendarEventItem

UrgencyPhase = Literal["no_deadline", "early", "building", "crunch", "same_day", "overdue"]
TimeBucket = Literal["morning", "afternoon", "evening", "night"]

PHASE_LABELS: dict[str, str] = {
    "no_deadline": "Sin fecha",
    "early": "Con margen (3+ días)",
    "building": "Esta semana (1-3 días)",
    "crunch": "Mañana",
    "same_day": "Hoy",
    "overdue": "Vencida",
}

BUCKET_LABELS: dict[str, str] = {
    "morning": "mañana",
    "afternoon": "tarde",
    "evening": "noche",
    "night": "madrugada",
}


@dataclass(slots=True)
class TimeContext:
    """Deterministic temporal snapshot used by Context, Learning and Momentum agents."""

    local_now: datetime
    local_hour: int
    time_bucket: TimeBucket
    urgency_phase: UrgencyPhase
    hours_left: float | None
    minutes_until_next_event: int | None
    next_event_title: str | None
    in_event_now: bool
    free_window_minutes: int
    load_next_72h: int

    @property
    def context_key(self) -> str:
        return f"{self.urgency_phase}:{self.time_bucket}"


def resolve_tz(timezone_name: str | None) -> ZoneInfo:
    try:
        return ZoneInfo(timezone_name or "America/Guayaquil")
    except ZoneInfoNotFoundError:
        return ZoneInfo("UTC")


def bucket_for_hour(hour: int) -> TimeBucket:
    if 5 <= hour < 12:
        return "morning"
    if 12 <= hour < 18:
        return "afternoon"
    if 18 <= hour < 23:
        return "evening"
    return "night"


def phase_for_hours(hours_left: float | None) -> UrgencyPhase:
    if hours_left is None:
        return "no_deadline"
    if hours_left < 0:
        return "overdue"
    if hours_left <= 8:
        return "same_day"
    if hours_left <= 24:
        return "crunch"
    if hours_left <= 72:
        return "building"
    return "early"


def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo else dt.replace(tzinfo=UTC)


def compute_time_context(
    *,
    now_utc: datetime | None = None,
    timezone_name: str | None = None,
    deadline: datetime | None = None,
    events: list[CalendarEventItem] | None = None,
    pending_deadlines: list[datetime | None] | None = None,
    default_free_minutes: int = 90,
) -> TimeContext:
    now = _aware(now_utc or datetime.now(UTC))
    tz = resolve_tz(timezone_name)
    local_now = now.astimezone(tz)

    hours_left: float | None = None
    if deadline is not None:
        hours_left = round((_aware(deadline) - now).total_seconds() / 3600.0, 2)

    minutes_to_next: int | None = None
    next_title: str | None = None
    in_event = False
    for ev in sorted(events or [], key=lambda e: _aware(e.start_at)):
        start, end = _aware(ev.start_at), _aware(ev.end_at)
        if start <= now < end:
            in_event = True
            next_title = ev.title
            minutes_to_next = 0
            break
        if start > now:
            minutes_to_next = int((start - now).total_seconds() // 60)
            next_title = ev.title
            break

    if in_event:
        free_window = 0
    elif minutes_to_next is not None:
        free_window = max(0, min(default_free_minutes, minutes_to_next - 5))
    else:
        free_window = default_free_minutes

    load = 0
    for dl in pending_deadlines or []:
        if dl is None:
            continue
        hrs = (_aware(dl) - now).total_seconds() / 3600.0
        if 0 <= hrs <= 72:
            load += 1

    return TimeContext(
        local_now=local_now,
        local_hour=local_now.hour,
        time_bucket=bucket_for_hour(local_now.hour),
        urgency_phase=phase_for_hours(hours_left),
        hours_left=hours_left,
        minutes_until_next_event=minutes_to_next,
        next_event_title=next_title,
        in_event_now=in_event,
        free_window_minutes=free_window,
        load_next_72h=load,
    )
