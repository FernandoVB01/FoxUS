from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field


class CalendarEventItem(BaseModel):
    id: str
    title: str
    start_at: datetime
    end_at: datetime
    source: str = "local"


class TaskContext(BaseModel):
    """Typed output of Context Agent (Section 4.2)."""

    user_id: uuid.UUID
    task_id: uuid.UUID
    task_title: str
    task_description: str | None = None
    deadline: datetime | None = None
    hours_until_deadline: float | None = None
    needs_pressure: bool = False
    urgency_level: Literal["low", "medium", "high", "critical"] = "medium"
    available_minutes: int = Field(default=5, ge=1, le=180)
    perceived_difficulty: int | None = Field(default=None, ge=1, le=5)
    recent_rejections_for_task: int = 0
    recent_sessions_count: int = 0
    upcoming_events: list[CalendarEventItem] = Field(default_factory=list)
    contextual_summary: str
    generation_source: Literal["gemma", "deterministic_rules"] = "deterministic_rules"
    model_name: str | None = None
    # Temporal awareness (deadline phase x local time x calendar)
    urgency_phase: str = "no_deadline"
    time_bucket: str = "afternoon"
    local_time_label: str | None = None
    minutes_until_next_event: int | None = None
    next_event_title: str | None = None
    load_next_72h: int = 0


class MicrotaskProposal(BaseModel):
    """Typed output of Microtask Agent (Section 4.3)."""

    task_id: uuid.UUID
    title: str = Field(min_length=3, max_length=255)
    lcd_short_title: str = Field(min_length=1, max_length=16)
    description: str = Field(min_length=5, max_length=1000)
    steps: list[str] = Field(default_factory=list)
    # Up to 2h so MomentumAgent chain links (e.g. "6 slides in 35 min") fit the same contract
    estimated_seconds: int = Field(default=180, ge=30, le=7200)
    success_criteria: str = Field(min_length=3, max_length=500)
    rationale: str = Field(min_length=3, max_length=500)
    difficulty_adapted: bool = False
    generation_source: Literal["gemma", "deterministic_fallback"]
    model_name: str


class HookProposal(BaseModel):
    """Typed output of Hook Agent (Section 4.4)."""

    hook_type: Literal["challenge", "fox_bzz", "curious_prompt", "ambient_nudge"]
    visual_mode_name: str
    message: str = Field(min_length=3, max_length=280)
    coach_voice_lines: list[str] = Field(default_factory=list)
    ignition_minigame: Literal["catch_fox", "spark_combo"] = "catch_fox"
    intensity: int = Field(default=2, ge=1, le=3)
    channel: Literal["in_app", "esp32", "both"] = "both"
    suggested_delay_seconds: int = Field(default=0, ge=0, le=300)
    sound_id: str
    animation_id: str
    generation_source: Literal["gemma", "deterministic_fallback"]
    model_name: str


class LearningSnapshot(BaseModel):
    """Typed output of Learning Agent (Section 4.5)."""

    recommended_hook_type: Literal["challenge", "fox_bzz"]
    acceptance_rate: float = 0.0
    start_rate: float = 0.0
    completion_rate: float = 0.0
    rejection_rate: float = 0.0
    scores_by_hook: dict[str, float] = Field(default_factory=dict)
    strategy: str = "adaptive_ucb_v1"
    # Contextual technique bandit (phase x time of day)
    recommended_technique: str | None = None
    technique_label: str | None = None
    technique_reason: str | None = None
    technique_scores: dict[str, float] = Field(default_factory=dict)
    context_key: str | None = None


class PolicyDecision(BaseModel):
    """Typed output of deterministic Policy Engine (Section 4.6)."""

    allowed: bool
    effective_intensity: int = Field(ge=0, le=3)
    effective_channel: Literal["in_app", "esp32", "both", "none"]
    audio_allowed: bool
    reasons: list[str] = Field(default_factory=list)
    cooldown_remaining_seconds: int = 0


class GenerateMissionRequest(BaseModel):
    task_id: uuid.UUID
    available_minutes: int = Field(default=5, ge=1, le=120)
    perceived_difficulty: int | None = Field(default=None, ge=1, le=5)
    requested_mode: Literal["auto", "challenge", "fox_bzz"] = "auto"
    dispatch_to_device: bool = True
    ignore_cooldown_for_demo: bool = False


class ChainBrief(BaseModel):
    """Domino-effect info attached to a mission when it is a chain link (MomentumAgent)."""

    chain_id: uuid.UUID
    link_id: uuid.UUID
    link_index: int
    links_completed: int = 0
    multiplier: float = 1.0
    technique: str
    technique_label: str
    target_minutes: int
    target_units: str | None = None
    suggested_evidence: Literal["photo", "voice", "video"] = "photo"
    story_caption: str | None = None
    expires_at: datetime | None = None


class GenerateMissionResponse(BaseModel):
    """Full mission payload returned to Android APK and auditable in Agent Debug."""

    schema_version: int = 1
    trace_id: str
    task_id: uuid.UUID
    microtask_id: uuid.UUID | None = None
    hook_id: uuid.UUID | None = None
    is_simulated_ai: bool
    policy: PolicyDecision
    context: TaskContext
    microtask: MicrotaskProposal
    hook: HookProposal
    learning: LearningSnapshot
    dispatched_device_command_id: uuid.UUID | None = None
    chain: ChainBrief | None = None


class PressureEvaluationRequest(BaseModel):
    """Evaluates whether FOX should proactively interrupt (Duolingo-style smart pressure)."""

    task_id: uuid.UUID | None = None
    demo_force: bool = False
    dispatch_to_device: bool = True


class PressureEvaluationResponse(BaseModel):
    should_interrupt: bool
    urgency_score: int = Field(ge=0, le=100)
    reason_short: str
    recommended_hook: Literal["challenge", "fox_bzz"] = "challenge"
    mission: GenerateMissionResponse | None = None


class AgentTraceStepRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    trace_id: str
    agent_name: str
    model_name: str | None = None
    duration_ms: int | None = None
    status: str
    input_summary: dict[str, Any] | None = None
    output_summary: dict[str, Any] | None = None
    created_at: datetime


class AgentTraceTimelineResponse(BaseModel):
    trace_id: str
    total_duration_ms: int
    used_real_gemma: bool
    models_invoked: list[str]
    steps: list[AgentTraceStepRead]
