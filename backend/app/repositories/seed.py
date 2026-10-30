from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.models.entities import (
    AgentTrace,
    Device,
    DeviceCommand,
    FocusSession,
    Hook,
    Microtask,
    RewardTransaction,
    SessionEvent,
    Task,
    User,
    UserHookStat,
    UserPreference,
)

DEMO_USER_ID = uuid.UUID("11111111-2222-3333-4444-555555555555")
DEMO_USER_EMAIL = "demo@foxus.local"
DEMO_DEVICE_ID = uuid.UUID("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")


def ensure_demo_user_and_seed(db: Session) -> User:
    """Idempotently ensure the controlled demo user, preferences, device, and starter tasks exist."""
    user = db.get(User, DEMO_USER_ID)
    if user is None:
        existing_by_email = db.scalar(select(User).where(User.email == DEMO_USER_EMAIL))
        if existing_by_email is not None:
            user = existing_by_email
        else:
            user = User(
                id=DEMO_USER_ID,
                display_name="Estudiante ESPOL (Demo)",
                email=DEMO_USER_EMAIL,
            )
            db.add(user)
            db.flush()

    prefs = db.scalar(select(UserPreference).where(UserPreference.user_id == user.id))
    if prefs is None:
        prefs = UserPreference(
            user_id=user.id,
            intervention_level=2,
            preferred_hook="auto",
            audio_enabled=True,
            music_enabled=False,
            quiet_hours_start="23:30",
            quiet_hours_end="06:30",
            timezone="America/Guayaquil",
        )
        db.add(prefs)

    for hook_type in ("challenge", "fox_bzz"):
        stat = db.scalar(
            select(UserHookStat).where(
                UserHookStat.user_id == user.id,
                UserHookStat.hook_type == hook_type,
            )
        )
        if stat is None:
            db.add(
                UserHookStat(
                    user_id=user.id,
                    hook_type=hook_type,
                    shown_count=0,
                    accepted_count=0,
                    started_count=0,
                    completed_count=0,
                    rejected_count=0,
                )
            )

    device = db.get(Device, DEMO_DEVICE_ID)
    if device is None:
        db.add(
            Device(
                id=DEMO_DEVICE_ID,
                user_id=user.id,
                device_name="FOXUS Watch ESP32 Proto",
                device_type="esp32_watch",
                status="idle",
            )
        )

    existing_tasks_count = db.scalars(select(Task).where(Task.user_id == user.id)).all()
    if not existing_tasks_count:
        now = datetime.now(UTC)
        db.add_all(
            [
                Task(
                    user_id=user.id,
                    title="Preparar 12 diapositivas para mi exposición",
                    description=(
                        "Presentación final del proyecto de arquitectura de software y demostración "
                        "con resultados experimentales."
                    ),
                    source="manual",
                    deadline=now + timedelta(hours=18),
                    status="pending",
                ),
                Task(
                    user_id=user.id,
                    title="Estudiar cinco capítulos de cálculo vectorial",
                    description="Repasar integrales de línea, teorema de Green y ejercicios tipo examen.",
                    source="manual",
                    deadline=now + timedelta(days=2),
                    status="pending",
                ),
            ]
        )

    db.commit()
    db.refresh(user)
    return user


def reset_demo_state(db: Session) -> User:
    """Cleanly reset transient demo state while preserving schema integrity."""
    user = ensure_demo_user_and_seed(db)
    uid = user.id

    db.execute(delete(AgentTrace).where(AgentTrace.user_id == uid))
    db.execute(delete(RewardTransaction).where(RewardTransaction.user_id == uid))
    db.execute(delete(FocusSession).where(FocusSession.user_id == uid))
    db.execute(delete(Hook).where(Hook.user_id == uid))
    db.execute(delete(Task).where(Task.user_id == uid))
    db.execute(delete(UserHookStat).where(UserHookStat.user_id == uid))

    devices = db.scalars(select(Device).where(Device.user_id == uid)).all()
    for dev in devices:
        db.execute(delete(DeviceCommand).where(DeviceCommand.device_id == dev.id))
        dev.status = "idle"

    db.execute(
        delete(SessionEvent).where(SessionEvent.session_id.is_(None))
    )
    db.commit()
    return ensure_demo_user_and_seed(db)
