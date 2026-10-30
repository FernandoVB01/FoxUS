from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Protocol
import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.entities import Task


@dataclass(slots=True)
class ExternalTaskRecord:
    external_id: str
    title: str
    description: str | None
    deadline: datetime | None
    source: str


class TasksProvider(Protocol):
    """Contract for reading user tasks without coupling agents to a single source."""

    def list_pending_tasks(self, user_id: uuid.UUID) -> list[ExternalTaskRecord]: ...


class LocalTasksProvider:
    """MVP provider backed by PostgreSQL `tasks` table."""

    def __init__(self, db: Session) -> None:
        self._db = db

    def list_pending_tasks(self, user_id: uuid.UUID) -> list[ExternalTaskRecord]:
        stmt = (
            select(Task)
            .where(Task.user_id == user_id, Task.status != "completed")
            .order_by(Task.created_at.desc())
        )
        rows = self._db.scalars(stmt).all()
        return [
            ExternalTaskRecord(
                external_id=row.external_id or str(row.id),
                title=row.title,
                description=row.description,
                deadline=row.deadline,
                source=row.source,
            )
            for row in rows
        ]


class GoogleTasksProvider:
    """
    Read-only Google Tasks API v1 adapter prepared for OAuth 2.0 native/web tokens.
    Note (Section 12): Never marks external Google Tasks as completed when only a
    FOXUS microtask finishes.
    """

    BASE_URL = "https://tasks.googleapis.com/tasks/v1"

    def __init__(self, oauth_access_token: str, timeout_seconds: float = 10.0) -> None:
        self._access_token = oauth_access_token
        self._timeout = timeout_seconds

    def list_pending_tasks(self, user_id: uuid.UUID) -> list[ExternalTaskRecord]:
        if not self._access_token:
            return []
        headers = {"Authorization": f"Bearer {self._access_token}"}
        results: list[ExternalTaskRecord] = []
        seen_ids: set[str] = set()

        with httpx.Client(timeout=self._timeout) as client:
            lists_resp = client.get(
                f"{self.BASE_URL}/users/@me/lists",
                params={"maxResults": 10},
                headers=headers,
            )
            lists_resp.raise_for_status()
            task_lists = lists_resp.json().get("items", []) or [{"id": "@default", "title": "Mis tareas"}]

            raw_entries: list[tuple[str, ExternalTaskRecord]] = []
            for tlist in task_lists[:10]:
                list_id = tlist.get("id", "@default")
                list_title = tlist.get("title", "")
                resp = client.get(
                    f"{self.BASE_URL}/lists/{list_id}/tasks",
                    params={"showCompleted": "false", "showHidden": "false", "maxResults": 100},
                    headers=headers,
                )
                resp.raise_for_status()
                payload = resp.json()

                for item in payload.get("items", []):
                    title_raw = str(item.get("title", "")).strip()
                    item_id = str(item.get("id", "")).strip()
                    if not title_raw or not item_id or item_id in seen_ids:
                        continue
                    seen_ids.add(item_id)
                    due_raw = item.get("due")
                    due_dt: datetime | None = None
                    if due_raw:
                        parsed_due = datetime.fromisoformat(due_raw.replace("Z", "+00:00"))
                        # Google Tasks REST API v1 strips time-of-day to 00:00:00Z.
                        # Shift date-only deadlines to 23:59 local Ecuador time (UTC-5 -> +28h59m)
                        # so tasks due today (e.g., 20:30) are treated as due tonight instead of yesterday.
                        if due_raw.endswith("T00:00:00.000Z") or due_raw.endswith("T00:00:00Z"):
                            due_dt = parsed_due + timedelta(hours=28, minutes=59)
                        else:
                            due_dt = parsed_due

                    notes = item.get("notes")
                    desc = (
                        f"[Google Tasks · {list_title}] {notes}"
                        if notes and list_title
                        else (notes or (f"Sincronizada desde Google Tasks ({list_title})" if list_title else None))
                    )
                    updated_iso = str(item.get("updated", ""))
                    raw_entries.append(
                        (
                            updated_iso,
                            ExternalTaskRecord(
                                external_id=f"gtask:{item_id}",
                                title=title_raw[:250],
                                description=desc,
                                deadline=due_dt,
                                source="google_tasks",
                            ),
                        )
                    )

            # Newest updated Google Tasks first
            raw_entries.sort(key=lambda pair: pair[0], reverse=True)
            results = [rec for _, rec in raw_entries]
        return results
