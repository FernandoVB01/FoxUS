from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field


class RewardTransactionRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    user_id: uuid.UUID
    session_id: uuid.UUID | None = None
    reward_type: str
    xp_amount: int
    idempotency_key: str
    created_at: datetime


class HookStatRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    hook_type: str
    shown_count: int
    accepted_count: int
    started_count: int
    completed_count: int
    rejected_count: int
    updated_at: datetime


class RewardProfileResponse(BaseModel):
    user_id: uuid.UUID
    display_name: str
    total_xp: int
    level: int
    xp_in_current_level: int
    xp_for_next_level: int
    progress_ratio: float
    completed_microtasks_count: int
    started_sessions_count: int
    achievements: list[str]
    hook_stats: list[HookStatRead] = Field(default_factory=list)


class UserPreferenceRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    user_id: uuid.UUID
    intervention_level: int
    preferred_hook: str
    audio_enabled: bool
    music_enabled: bool
    quiet_hours_start: str | None = None
    quiet_hours_end: str | None = None
    timezone: str
    updated_at: datetime


class UserPreferenceUpdate(BaseModel):
    intervention_level: int | None = Field(default=None, ge=0, le=3)
    preferred_hook: Literal["auto", "challenge", "fox_bzz"] | None = None
    audio_enabled: bool | None = None
    music_enabled: bool | None = None
    quiet_hours_start: str | None = Field(default=None, pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    quiet_hours_end: str | None = Field(default=None, pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    timezone: str | None = Field(default=None, max_length=64)


class DeviceRegisterRequest(BaseModel):
    device_name: str = Field(min_length=2, max_length=120, examples=["FOXUS Watch ESP32"])
    device_type: Literal["esp32_watch", "android_simulator"] = "esp32_watch"


class DeviceRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    user_id: uuid.UUID
    device_name: str
    device_type: str
    last_seen_at: datetime | None = None
    status: str


class DeviceCommandRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    device_id: uuid.UUID
    command_type: str
    payload: dict[str, Any]
    status: str
    created_at: datetime
    acknowledged_at: datetime | None = None


class NextDeviceCommandResponse(BaseModel):
    has_command: bool
    command: DeviceCommandRead | None = None
    poll_interval_seconds: int = 3


class DeviceEventRequest(BaseModel):
    event_type: Literal[
        "BUTTON_ACCEPT",
        "BUTTON_POSTPONE",
        "BUTTON_COMPLETE",
        "COMMAND_ACK",
    ]
    command_id: uuid.UUID | None = None
    microtask_id: uuid.UUID | None = None
    hook_id: uuid.UUID | None = None
    session_id: uuid.UUID | None = None
    idempotency_key: str | None = Field(default=None, max_length=160)
    source: Literal["esp32", "simulator"] = "esp32"


class DeviceEventResponse(BaseModel):
    accepted: bool
    event_type: str
    lcd_line_1: str = Field(max_length=16)
    lcd_line_2: str = Field(max_length=16)
    session_id: uuid.UUID | None = None
    xp_awarded: int = 0
    total_xp: int = 0


class DeviceHeartbeatRequest(BaseModel):
    status: Literal["online", "idle", "mission_running", "offline"] = "idle"
    firmware_version: str | None = "0.1.0"
    rssi_dbm: int | None = None
