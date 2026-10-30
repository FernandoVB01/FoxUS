from __future__ import annotations

import uuid
from datetime import UTC, datetime
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agents.learning_agent import LearningAgent
from app.api.deps import get_current_demo_user
from app.core.database import get_db
from app.core.security import verify_device_access
from app.models.entities import (
    Device,
    DeviceCommand,
    FocusSession,
    Hook,
    SessionEvent,
    User,
)
from app.schemas.domain import (
    DeviceCommandRead,
    DeviceEventRequest,
    DeviceEventResponse,
    DeviceHeartbeatRequest,
    DeviceRead,
    DeviceRegisterRequest,
    NextDeviceCommandResponse,
)
from app.services.reward_engine import RewardEngine
from app.services.session_service import SessionLifecycleService

router = APIRouter(prefix="/devices", tags=["ESP32 & Hardware Simulator"])


@router.get(
    "",
    response_model=list[DeviceRead],
    summary="List registered physical ESP32 and simulator devices for the user",
)
def list_devices(
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> list[Device]:
    stmt = select(Device).where(Device.user_id == user.id).order_by(Device.device_name.asc())
    return list(db.scalars(stmt).all())


@router.post(
    "/register",
    response_model=DeviceRead,
    status_code=status.HTTP_201_CREATED,
    summary="Register or reconnect an ESP32 watch prototype or Android hardware simulator",
)
def register_device(
    payload: DeviceRegisterRequest,
    _auth: str = Depends(verify_device_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> Device:
    existing = db.scalar(
        select(Device).where(
            Device.user_id == user.id,
            Device.device_name == payload.device_name.strip(),
            Device.device_type == payload.device_type,
        )
    )
    now = datetime.now(UTC)
    if existing is not None:
        existing.last_seen_at = now
        existing.status = "idle"
        db.commit()
        db.refresh(existing)
        return existing

    dev = Device(
        user_id=user.id,
        device_name=payload.device_name.strip(),
        device_type=payload.device_type,
        last_seen_at=now,
        status="idle",
    )
    db.add(dev)
    db.commit()
    db.refresh(dev)
    return dev


@router.get(
    "/{device_id}/commands/next",
    response_model=NextDeviceCommandResponse,
    summary="Poll next pending command for ESP32 watch (non-blocking bounded HTTP polling)",
)
def get_next_device_command(
    device_id: uuid.UUID,
    _auth: str = Depends(verify_device_access),
    db: Session = Depends(get_db),
) -> NextDeviceCommandResponse:
    dev = db.get(Device, device_id)
    if dev is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found.")

    dev.last_seen_at = datetime.now(UTC)
    cmd = db.scalar(
        select(DeviceCommand)
        .where(
            DeviceCommand.device_id == device_id,
            DeviceCommand.status.in_(("pending", "delivered")),
        )
        .order_by(DeviceCommand.created_at.asc())
    )
    if cmd is not None and cmd.status == "pending":
        cmd.status = "delivered"

    db.commit()
    if cmd is not None:
        db.refresh(cmd)

    return NextDeviceCommandResponse(
        has_command=cmd is not None,
        command=DeviceCommandRead.model_validate(cmd) if cmd is not None else None,
        poll_interval_seconds=3,
    )


@router.post(
    "/{device_id}/events",
    response_model=DeviceEventResponse,
    summary="Handle idempotent physical ESP32 button events (ACCEPT, POSTPONE, COMPLETE, ACK)",
)
def post_device_event(
    device_id: uuid.UUID,
    payload: DeviceEventRequest,
    _auth: str = Depends(verify_device_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> DeviceEventResponse:
    dev = db.get(Device, device_id)
    if dev is None or dev.user_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found.")

    now = datetime.now(UTC)
    dev.last_seen_at = now

    cmd: DeviceCommand | None = None
    if payload.command_id is not None:
        cmd = db.get(DeviceCommand, payload.command_id)
        if cmd is not None and cmd.device_id == dev.id and cmd.acknowledged_at is None:
            cmd.status = "acknowledged"
            cmd.acknowledged_at = now

    microtask_id = payload.microtask_id
    hook_id = payload.hook_id
    if cmd is not None and isinstance(cmd.payload, dict):
        if microtask_id is None and cmd.payload.get("microtask_id"):
            microtask_id = uuid.UUID(str(cmd.payload["microtask_id"]))
        if hook_id is None and cmd.payload.get("hook_id"):
            hook_id = uuid.UUID(str(cmd.payload["hook_id"]))

    reward_engine = RewardEngine()

    if payload.event_type == "COMMAND_ACK":
        db.commit()
        return DeviceEventResponse(
            accepted=True,
            event_type=payload.event_type,
            lcd_line_1="MISSION READY",
            lcd_line_2="Press ACCEPT",
            total_xp=reward_engine.get_user_total_xp(db, user.id),
        )

    if payload.event_type == "BUTTON_ACCEPT":
        if microtask_id is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="microtask_id or command_id with microtask_id is required to accept.",
            )
        dev.status = "mission_running"
        svc = SessionLifecycleService(db=db, reward_engine=reward_engine)
        res = svc.start_session(
            user=user,
            microtask_id=microtask_id,
            hook_id=hook_id,
            source=payload.source,
            idempotency_key=payload.idempotency_key,
        )
        awarded = sum(g.xp_awarded for g in res.rewards_granted)
        return DeviceEventResponse(
            accepted=True,
            event_type=payload.event_type,
            lcd_line_1="FOCUS MODE",
            lcd_line_2=f"START +{awarded} XP" if awarded > 0 else "TIMER RUNNING",
            session_id=res.session.id,
            xp_awarded=awarded,
            total_xp=res.total_xp,
        )

    if payload.event_type == "BUTTON_POSTPONE":
        dev.status = "idle"
        if hook_id is not None:
            hook_row = db.get(Hook, hook_id)
            if hook_row is not None and hook_row.status not in ("postponed", "rejected"):
                hook_row.status = "postponed"
                LearningAgent(db=db).record_outcome(
                    user_id=user.id, hook_type=hook_row.hook_type, outcome="rejected"
                )
        if payload.session_id is not None:
            SessionLifecycleService(db=db, reward_engine=reward_engine).stop_session(
                user=user,
                session_id=payload.session_id,
                source=payload.source,
                reason="postponed",
            )
        else:
            db.add(
                SessionEvent(
                    session_id=None,
                    event_type="DEVICE_MISSION_POSTPONED",
                    source=payload.source,
                    event_metadata={"device_id": str(device_id)},
                )
            )
            db.commit()

        return DeviceEventResponse(
            accepted=True,
            event_type=payload.event_type,
            lcd_line_1="FOXUS SNOOZED",
            lcd_line_2="Waiting mission",
            total_xp=reward_engine.get_user_total_xp(db, user.id),
        )

    # BUTTON_COMPLETE
    dev.status = "idle"
    target_session_id = payload.session_id
    if target_session_id is None and microtask_id is not None:
        active_sess = db.scalar(
            select(FocusSession)
            .where(
                FocusSession.user_id == user.id,
                FocusSession.microtask_id == microtask_id,
            )
            .order_by(FocusSession.created_at.desc())
        )
        if active_sess is not None:
            target_session_id = active_sess.id

    if target_session_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No active session found to complete. Press ACCEPT first.",
        )

    svc = SessionLifecycleService(db=db, reward_engine=reward_engine)
    res = svc.complete_session(
        user=user,
        session_id=target_session_id,
        source=payload.source,
        idempotency_key=payload.idempotency_key,
    )
    awarded = sum(g.xp_awarded for g in res.rewards_granted)
    return DeviceEventResponse(
        accepted=True,
        event_type=payload.event_type,
        lcd_line_1="MISSION DONE!",
        lcd_line_2=f"+{awarded} XP" if awarded > 0 else "ALREADY SAVED",
        session_id=res.session.id,
        xp_awarded=awarded,
        total_xp=res.total_xp,
    )


@router.post(
    "/{device_id}/heartbeat",
    response_model=DeviceRead,
    summary="Report ESP32 watch connectivity and state-machine status",
)
def post_device_heartbeat(
    device_id: uuid.UUID,
    payload: DeviceHeartbeatRequest,
    _auth: str = Depends(verify_device_access),
    user: User = Depends(get_current_demo_user),
    db: Session = Depends(get_db),
) -> Device:
    dev = db.get(Device, device_id)
    if dev is None or dev.user_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found.")

    dev.last_seen_at = datetime.now(UTC)
    dev.status = payload.status
    db.commit()
    db.refresh(dev)
    return dev
