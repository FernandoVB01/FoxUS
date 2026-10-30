from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field


class SessionStartRequest(BaseModel):
    microtask_id: uuid.UUID
    hook_id: uuid.UUID | None = None
    source: Literal["android", "esp32", "simulator"] = "android"
    idempotency_key: str | None = Field(default=None, max_length=160)


class SessionCompleteRequest(BaseModel):
    source: Literal["android", "esp32", "simulator"] = "android"
    voluntary_continuity: bool = False
    actual_duration_seconds: int | None = Field(default=None, ge=0, le=86400)
    mark_parent_task_done: bool = False
    idempotency_key: str | None = Field(default=None, max_length=160)


class SessionStopRequest(BaseModel):
    source: Literal["android", "esp32", "simulator"] = "android"
    reason: Literal["postponed", "stopped_by_user", "silenced", "disturbance_reported"] = (
        "stopped_by_user"
    )


class HookDismissRequest(BaseModel):
    source: Literal["android", "esp32", "simulator"] = "android"
    reason: Literal["postponed", "silenced", "rejected"] = "postponed"


class HookDismissResponse(BaseModel):
    hook_id: uuid.UUID
    hook_type: str
    status: str
    recorded_event_type: str


class RewardGrantSummary(BaseModel):
    reward_type: str
    xp_awarded: int
    already_granted: bool
    idempotency_key: str


class SessionEventRead(BaseModel):
    model_config = ConfigDict(from_attributes=True, populate_by_name=True)

    id: uuid.UUID
    session_id: uuid.UUID | None = None
    event_type: str
    source: str
    metadata: dict[str, Any] = Field(default_factory=dict, validation_alias="event_metadata")
    occurred_at: datetime


class SessionRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    user_id: uuid.UUID
    microtask_id: uuid.UUID
    hook_id: uuid.UUID | None = None
    started_at: datetime | None = None
    ended_at: datetime | None = None
    status: str
    completion_source: str | None = None
    created_at: datetime


class SessionActionResponse(BaseModel):
    session: SessionRead
    rewards_granted: list[RewardGrantSummary] = Field(default_factory=list)
    total_xp: int
    current_level: int
    celebrate: bool = False


class RefereeVerdict(BaseModel):
    verified: bool
    evidence_type: Literal["photo", "voice"]
    confidence_score: int = Field(ge=0, le=100)
    referee_speech: str
    detected_progress: str
    next_tip: str
    generation_source: Literal["gemma", "deterministic_fallback"]
    model_name: str


class SessionEvidenceVerifyRequest(BaseModel):
    evidence_type: Literal["photo", "voice"]
    image_base64: str | None = None
    image_mime_type: str = "image/jpeg"
    voice_transcript: str | None = Field(default=None, max_length=1500)
    checked_steps_count: int = Field(default=0, ge=0, le=10)
    actual_duration_seconds: int | None = Field(default=None, ge=0, le=86400)
    source: Literal["android", "esp32", "simulator"] = "android"
    idempotency_key: str | None = Field(default=None, max_length=160)


class SessionEvidenceVerifyResponse(BaseModel):
    session_result: SessionActionResponse
    verdict: RefereeVerdict
    bonus_xp_awarded: int = 0
    trace_id: str
