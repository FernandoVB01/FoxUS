from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta
from typing import Protocol
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.entities import ExternalIntegration
from app.schemas.agents import CalendarEventItem


class CalendarProvider(Protocol):
    """Contract for reading upcoming calendar events (read-only)."""

    def get_upcoming_events(
        self, user_id: uuid.UUID, horizon_hours: int = 24
    ) -> list[CalendarEventItem]: ...


class LocalCalendarProvider:
    """
    Deterministic local calendar provider for MVP / offline operation.
    Never invents random deadlines; returns explicit demo schedule blocks if enabled.
    """

    def __init__(self, events: list[CalendarEventItem] | None = None) -> None:
        self._events = events or []

    def get_upcoming_events(
        self, user_id: uuid.UUID, horizon_hours: int = 24
    ) -> list[CalendarEventItem]:
        now = datetime.now(UTC)
        cutoff = now + timedelta(hours=horizon_hours)
        return [ev for ev in self._events if now <= ev.end_at and ev.start_at <= cutoff]


class DatabaseSyncedCalendarProvider:
    """Reads synced real Google Calendar / iCal events stored in `external_integrations`."""

    def __init__(self, db: Session) -> None:
        self._db = db

    def get_upcoming_events(
        self, user_id: uuid.UUID, horizon_hours: int = 24
    ) -> list[CalendarEventItem]:
        integ = self._db.scalar(
            select(ExternalIntegration).where(
                ExternalIntegration.user_id == user_id,
                ExternalIntegration.provider == "google",
            )
        )
        if integ is None or not integ.cached_events_json:
            return []

        now = datetime.now(UTC)
        cutoff = now + timedelta(hours=horizon_hours)
        results: list[CalendarEventItem] = []
        for raw in integ.cached_events_json:
            try:
                ev = CalendarEventItem.model_validate(raw)
                start_dt = ev.start_at if ev.start_at.tzinfo else ev.start_at.replace(tzinfo=UTC)
                end_dt = ev.end_at if ev.end_at.tzinfo else ev.end_at.replace(tzinfo=UTC)
                if now <= end_dt and start_dt <= cutoff:
                    results.append(ev)
            except Exception:
                continue
        results.sort(key=lambda e: e.start_at)
        return results


class GoogleCalendarProvider:
    """Read-only Google Calendar API v3 adapter for OAuth 2.0 minimal scopes."""

    BASE_URL = "https://www.googleapis.com/calendar/v3"

    def __init__(self, oauth_access_token: str, timeout_seconds: float = 10.0) -> None:
        self._access_token = oauth_access_token
        self._timeout = timeout_seconds

    def get_upcoming_events(
        self, user_id: uuid.UUID, horizon_hours: int = 72
    ) -> list[CalendarEventItem]:
        if not self._access_token:
            return []
        now = datetime.now(UTC)
        start_window = now - timedelta(hours=2)
        end = now + timedelta(hours=horizon_hours)
        headers = {"Authorization": f"Bearer {self._access_token}"}
        params = {
            "timeMin": start_window.isoformat(),
            "timeMax": end.isoformat(),
            "singleEvents": "true",
            "orderBy": "startTime",
            "maxResults": 25,
        }
        with httpx.Client(timeout=self._timeout) as client:
            resp = client.get(
                f"{self.BASE_URL}/calendars/primary/events",
                params=params,
                headers=headers,
            )
            resp.raise_for_status()
            payload = resp.json()

        events: list[CalendarEventItem] = []
        for item in payload.get("items", []):
            start_obj = item.get("start", {})
            end_obj = item.get("end", {})
            start_raw = start_obj.get("dateTime") or start_obj.get("date")
            end_raw = end_obj.get("dateTime") or end_obj.get("date")
            if not start_raw or not end_raw:
                continue
            start_dt = _parse_google_date_or_dt(start_raw)
            end_dt = _parse_google_date_or_dt(end_raw)
            if start_dt and end_dt:
                events.append(
                    CalendarEventItem(
                        id=str(item.get("id", uuid.uuid4().hex[:10])),
                        title=str(item.get("summary", "Evento Google Calendar")),
                        start_at=start_dt,
                        end_at=end_dt,
                        source="google_calendar",
                    )
                )
        return events


def _parse_google_date_or_dt(raw: str) -> datetime | None:
    try:
        if "T" in raw:
            dt = datetime.fromisoformat(raw.replace("Z", "+00:00"))
            return dt if dt.tzinfo else dt.replace(tzinfo=UTC)
        # All-day YYYY-MM-DD
        dt = datetime.strptime(raw[:10], "%Y-%m-%d").replace(tzinfo=UTC)
        return dt
    except ValueError:
        return None


def parse_ics_datetime(raw_prop_key: str, raw_val: str) -> datetime | None:
    """Parse RFC-5545 DTSTART/DTEND lines (UTC 'Z', TZID, or all-day DATE)."""
    val = raw_val.strip()
    tz = UTC
    if "TZID=" in raw_prop_key:
        for param in raw_prop_key.split(";")[1:]:
            if param.upper().startswith("TZID="):
                tz_name = param.split("=", 1)[1].strip()
                try:
                    tz = ZoneInfo(tz_name)
                except ZoneInfoNotFoundError:
                    tz = UTC
    try:
        if val.endswith("Z"):
            return datetime.strptime(val, "%Y%m%dT%H%M%SZ").replace(tzinfo=UTC)
        if "T" in val:
            local_dt = datetime.strptime(val[:15], "%Y%m%dT%H%M%S").replace(tzinfo=tz)
            return local_dt.astimezone(UTC)
        if len(val) >= 8:
            return datetime.strptime(val[:8], "%Y%m%d").replace(tzinfo=UTC)
    except ValueError:
        return None
    return None


class ICalFeedCalendarProvider:
    """
    Zero-API-key Google Calendar adapter using the user's private/public `.ics` iCal URL
    (Google Calendar -> Configuración -> Dirección secreta en formato iCal).
    """

    def __init__(self, ical_url: str, timeout_seconds: float = 10.0) -> None:
        self._ical_url = ical_url.strip()
        self._timeout = timeout_seconds

    def get_upcoming_events(
        self, user_id: uuid.UUID, horizon_hours: int = 72
    ) -> list[CalendarEventItem]:
        if not self._ical_url:
            return []
        with httpx.Client(timeout=self._timeout, follow_redirects=True) as client:
            resp = client.get(self._ical_url)
            resp.raise_for_status()
            ics_text = resp.text

        return self.parse_ics_events(ics_text, horizon_hours=horizon_hours)

    @staticmethod
    def parse_ics_events(ics_text: str, horizon_hours: int = 72) -> list[CalendarEventItem]:
        # Unfold RFC-5545 folded lines
        unfolded_lines: list[str] = []
        for raw_line in ics_text.splitlines():
            if raw_line.startswith((" ", "\t")) and unfolded_lines:
                unfolded_lines[-1] += raw_line[1:]
            else:
                unfolded_lines.append(raw_line.rstrip("\r"))

        now = datetime.now(UTC)
        cutoff = now + timedelta(hours=horizon_hours)
        events: list[CalendarEventItem] = []

        in_vevent = False
        uid_val = ""
        summary_val = "Evento de Calendario"
        dt_start: datetime | None = None
        dt_end: datetime | None = None

        for line in unfolded_lines:
            upper = line.upper()
            if upper == "BEGIN:VEVENT":
                in_vevent = True
                uid_val = uuid.uuid4().hex[:10]
                summary_val = "Evento de Calendario"
                dt_start = None
                dt_end = None
            elif upper == "END:VEVENT":
                if in_vevent and dt_start is not None:
                    effective_end = dt_end or (dt_start + timedelta(hours=1))
                    if now <= effective_end and dt_start <= cutoff:
                        events.append(
                            CalendarEventItem(
                                id=uid_val,
                                title=summary_val[:180],
                                start_at=dt_start,
                                end_at=effective_end,
                                source="google_calendar_ics",
                            )
                        )
                in_vevent = False
            elif in_vevent and ":" in line:
                prop_key, prop_val = line.split(":", 1)
                base_name = prop_key.split(";", 1)[0].upper()
                if base_name == "UID":
                    uid_val = prop_val.strip()[:80]
                elif base_name == "SUMMARY":
                    summary_val = (
                        prop_val.replace("\\,", ",")
                        .replace("\\;", ";")
                        .replace("\\n", " ")
                        .strip()
                    ) or "Evento Google Calendar"
                elif base_name == "DTSTART":
                    dt_start = parse_ics_datetime(prop_key, prop_val)
                elif base_name == "DTEND":
                    dt_end = parse_ics_datetime(prop_key, prop_val)

        events.sort(key=lambda e: e.start_at)
        return events[:25]
