from __future__ import annotations

import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.api.deps import get_current_demo_user
from app.api.v1.integrations import auto_sync_google_if_connected
from app.core.database import get_db
from app.core.security import verify_demo_write_access
from app.models.entities import Task, User
from app.schemas.tasks import TaskCreate, TaskRead, TaskUpdate

router = APIRouter(prefix="/tasks", tags=["Tasks"])


@router.get("", response_model=list[TaskRead], summary="List user tasks and generated microtasks")
def list_tasks(
    status_filter: str | None = None,
    force_sync: bool = False,
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> list[Task]:
    auto_sync_google_if_connected(
        db=db,
        user=user,
        min_interval_seconds=0 if force_sync else 8,
    )
    stmt = (
        select(Task)
        .options(selectinload(Task.microtasks))
        .where(Task.user_id == user.id)
        .order_by(Task.needs_pressure.desc(), Task.created_at.desc())
    )
    if status_filter:
        stmt = stmt.where(Task.status == status_filter)
    return list(db.scalars(stmt).all())


@router.post(
    "",
    response_model=TaskRead,
    status_code=status.HTTP_201_CREATED,
    summary="Create a new manual task",
)
def create_task(
    payload: TaskCreate,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> Task:
    task = Task(
        user_id=user.id,
        title=payload.title.strip(),
        description=payload.description.strip() if payload.description else None,
        deadline=payload.deadline,
        needs_pressure=payload.needs_pressure,
        source=payload.source,
        external_id=payload.external_id,
        status="pending",
    )
    db.add(task)
    db.commit()
    stmt = select(Task).options(selectinload(Task.microtasks)).where(Task.id == task.id)
    created = db.scalar(stmt)
    assert created is not None
    return created


@router.get("/{task_id}", response_model=TaskRead, summary="Get a single task by ID")
def get_task(
    task_id: uuid.UUID,
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> Task:
    stmt = (
        select(Task)
        .options(selectinload(Task.microtasks))
        .where(Task.id == task_id, Task.user_id == user.id)
    )
    task = db.scalar(stmt)
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found.")
    return task


@router.patch("/{task_id}", response_model=TaskRead, summary="Update task pressure flag, deadline, or status")
def update_task(
    task_id: uuid.UUID,
    payload: TaskUpdate,
    _auth: str = Depends(verify_demo_write_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> Task:
    stmt = (
        select(Task)
        .options(selectinload(Task.microtasks))
        .where(Task.id == task_id, Task.user_id == user.id)
    )
    task = db.scalar(stmt)
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found.")

    updates = payload.model_dump(exclude_unset=True)
    for field_name, val in updates.items():
        if val is None and field_name in ("title", "needs_pressure", "status"):
            continue
        if field_name == "status" and val == "completed" and task.source != "manual":
            # Respect Section 12: never auto-complete external Google Tasks from FOXUS
            continue
        setattr(task, field_name, val)

    db.commit()
    refreshed = db.scalar(stmt)
    assert refreshed is not None
    return refreshed
