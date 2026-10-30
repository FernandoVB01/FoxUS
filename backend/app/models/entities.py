from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any
from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, JSONBType, utcnow


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str | None] = mapped_column(String(255), nullable=True, unique=True, index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    preferences: Mapped[UserPreference | None] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan"
    )
    tasks: Mapped[list[Task]] = relationship(back_populates="user", cascade="all, delete-orphan")
    hooks: Mapped[list[Hook]] = relationship(back_populates="user", cascade="all, delete-orphan")
    sessions: Mapped[list[FocusSession]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    reward_transactions: Mapped[list[RewardTransaction]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    hook_stats: Mapped[list[UserHookStat]] = relationship(
        back_populates="user", cascade="all, delete-orphan"
    )
    devices: Mapped[list[Device]] = relationship(back_populates="user", cascade="all, delete-orphan")


class UserPreference(Base):
    __tablename__ = "user_preferences"
    __table_args__ = (
        CheckConstraint(
            "intervention_level >= 0 AND intervention_level <= 3",
            name="intervention_level_range",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
        index=True,
    )
    intervention_level: Mapped[int] = mapped_column(Integer, nullable=False, default=2)
    preferred_hook: Mapped[str] = mapped_column(String(50), nullable=False, default="auto")
    audio_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    music_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    quiet_hours_start: Mapped[str | None] = mapped_column(String(5), nullable=True, default="23:00")
    quiet_hours_end: Mapped[str | None] = mapped_column(String(5), nullable=True, default="07:00")
    timezone: Mapped[str] = mapped_column(String(64), nullable=False, default="America/Guayaquil")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        server_default=func.now(),
    )

    user: Mapped[User] = relationship(back_populates="preferences")


class Task(Base):
    __tablename__ = "tasks"
    __table_args__ = (
        Index("ix_tasks_user_status", "user_id", "status"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    source: Mapped[str] = mapped_column(String(50), nullable=False, default="manual")
    external_id: Mapped[str | None] = mapped_column(String(255), nullable=True)
    deadline: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    needs_pressure: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    status: Mapped[str] = mapped_column(String(32), nullable=False, default="pending")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    user: Mapped[User] = relationship(back_populates="tasks")
    microtasks: Mapped[list[Microtask]] = relationship(
        back_populates="task", cascade="all, delete-orphan"
    )


class Microtask(Base):
    __tablename__ = "microtasks"
    __table_args__ = (
        CheckConstraint("estimated_seconds > 0", name="positive_estimated_seconds"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    task_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True
    )
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    estimated_seconds: Mapped[int] = mapped_column(Integer, nullable=False, default=180)
    success_criteria: Mapped[str] = mapped_column(String(500), nullable=False)
    model_name: Mapped[str] = mapped_column(String(120), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    task: Mapped[Task] = relationship(back_populates="microtasks")
    hooks: Mapped[list[Hook]] = relationship(back_populates="microtask", cascade="all, delete-orphan")
    sessions: Mapped[list[FocusSession]] = relationship(
        back_populates="microtask", cascade="all, delete-orphan"
    )


class Hook(Base):
    __tablename__ = "hooks"
    __table_args__ = (
        CheckConstraint("intensity >= 1 AND intensity <= 3", name="hook_intensity_range"),
        Index("ix_hooks_user_created", "user_id", "created_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    microtask_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("microtasks.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    hook_type: Mapped[str] = mapped_column(String(50), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    intensity: Mapped[int] = mapped_column(Integer, nullable=False, default=2)
    channel: Mapped[str] = mapped_column(String(50), nullable=False, default="both")
    status: Mapped[str] = mapped_column(String(32), nullable=False, default="shown")
    # Productivity technique suggested by LearningAgent/TechniqueAdvisor + temporal context key
    technique: Mapped[str | None] = mapped_column(String(40), nullable=True)
    context_key: Mapped[str | None] = mapped_column(String(40), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    user: Mapped[User] = relationship(back_populates="hooks")
    microtask: Mapped[Microtask] = relationship(back_populates="hooks")
    sessions: Mapped[list[FocusSession]] = relationship(back_populates="hook")


class FocusSession(Base):
    __tablename__ = "sessions"
    __table_args__ = (
        Index("ix_sessions_user_status", "user_id", "status"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    microtask_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("microtasks.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    hook_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("hooks.id", ondelete="SET NULL"), nullable=True, index=True
    )
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[str] = mapped_column(String(32), nullable=False, default="started")
    completion_source: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    user: Mapped[User] = relationship(back_populates="sessions")
    microtask: Mapped[Microtask] = relationship(back_populates="sessions")
    hook: Mapped[Hook | None] = relationship(back_populates="sessions")
    events: Mapped[list[SessionEvent]] = relationship(
        back_populates="session", cascade="all, delete-orphan"
    )
    rewards: Mapped[list[RewardTransaction]] = relationship(back_populates="session")


class SessionEvent(Base):
    __tablename__ = "session_events"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("sessions.id", ondelete="CASCADE"),
        nullable=True,
        index=True,
    )
    event_type: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    source: Mapped[str] = mapped_column(String(64), nullable=False)
    event_metadata: Mapped[dict[str, Any]] = mapped_column(
        "metadata", JSONBType, nullable=False, default=dict
    )
    occurred_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    session: Mapped[FocusSession | None] = relationship(back_populates="events")


class RewardTransaction(Base):
    __tablename__ = "reward_transactions"
    __table_args__ = (
        CheckConstraint("xp_amount > 0", name="positive_xp_amount"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    session_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("sessions.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    reward_type: Mapped[str] = mapped_column(String(64), nullable=False)
    xp_amount: Mapped[int] = mapped_column(Integer, nullable=False)
    idempotency_key: Mapped[str] = mapped_column(String(160), nullable=False, unique=True, index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    user: Mapped[User] = relationship(back_populates="reward_transactions")
    session: Mapped[FocusSession | None] = relationship(back_populates="rewards")


class UserHookStat(Base):
    __tablename__ = "user_hook_stats"
    __table_args__ = (
        UniqueConstraint("user_id", "hook_type", name="uq_user_hook_stats_user_hook"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    hook_type: Mapped[str] = mapped_column(String(50), nullable=False)
    shown_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    accepted_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    started_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    completed_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    rejected_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        server_default=func.now(),
    )

    user: Mapped[User] = relationship(back_populates="hook_stats")


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    device_name: Mapped[str] = mapped_column(String(120), nullable=False)
    device_type: Mapped[str] = mapped_column(String(50), nullable=False, default="esp32_watch")
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[str] = mapped_column(String(32), nullable=False, default="idle")

    user: Mapped[User] = relationship(back_populates="devices")
    commands: Mapped[list[DeviceCommand]] = relationship(
        back_populates="device", cascade="all, delete-orphan"
    )


class DeviceCommand(Base):
    __tablename__ = "device_commands"
    __table_args__ = (
        Index("ix_device_commands_device_status", "device_id", "status"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    device_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True
    )
    command_type: Mapped[str] = mapped_column(String(64), nullable=False)
    payload: Mapped[dict[str, Any]] = mapped_column(JSONBType, nullable=False, default=dict)
    status: Mapped[str] = mapped_column(String(32), nullable=False, default="pending")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )
    acknowledged_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    device: Mapped[Device] = relationship(back_populates="commands")


class AgentTrace(Base):
    __tablename__ = "agent_traces"
    __table_args__ = (
        Index("ix_agent_traces_trace_created", "trace_id", "created_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    trace_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )
    agent_name: Mapped[str] = mapped_column(String(64), nullable=False)
    model_name: Mapped[str | None] = mapped_column(String(120), nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    status: Mapped[str] = mapped_column(String(32), nullable=False, default="ok")
    input_summary: Mapped[dict[str, Any] | None] = mapped_column(JSONBType, nullable=True)
    output_summary: Mapped[dict[str, Any] | None] = mapped_column(JSONBType, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )


class ExternalIntegration(Base):
    __tablename__ = "external_integrations"
    __table_args__ = (
        UniqueConstraint("user_id", "provider", name="uq_external_integrations_user_provider"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    provider: Mapped[str] = mapped_column(String(40), nullable=False, default="google")
    auth_mode: Mapped[str] = mapped_column(String(32), nullable=False, default="none")
    connected_email: Mapped[str | None] = mapped_column(String(255), nullable=True)
    access_token: Mapped[str | None] = mapped_column(Text, nullable=True)
    refresh_token: Mapped[str | None] = mapped_column(Text, nullable=True)
    token_expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    calendar_ical_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    cached_events_json: Mapped[list[dict[str, Any]]] = mapped_column(
        JSONBType, nullable=False, default=list
    )
    last_synced_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        server_default=func.now(),
    )


class UserProfile(Base):
    """Public-facing FOXUS profile (handle, bio, visibility, daily streak bookkeeping)."""

    __tablename__ = "user_profiles"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
        index=True,
    )
    handle: Mapped[str] = mapped_column(String(40), nullable=False, unique=True, index=True)
    bio: Mapped[str | None] = mapped_column(String(160), nullable=True)
    public_enabled: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=True, server_default="true"
    )
    best_daily_streak: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    best_heat: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        server_default=func.now(),
    )


class MomentumChain(Base):
    """
    'Racha de Enganche' for one concrete activity (task). Each completed hook becomes a link;
    the chain heats up with richer evidence and breaks after 24 h idle or at the deadline.
    """

    __tablename__ = "momentum_chains"
    __table_args__ = (
        Index("ix_momentum_chains_user_status", "user_id", "status"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    task_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False, index=True
    )
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="active")
    links_completed: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    heat: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_link_completed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    broken_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )

    links: Mapped[list[ChainLink]] = relationship(
        back_populates="chain", cascade="all, delete-orphan", order_by="ChainLink.link_index"
    )


class ChainLink(Base):
    __tablename__ = "chain_links"
    __table_args__ = (
        UniqueConstraint("chain_id", "link_index", name="uq_chain_links_chain_index"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    chain_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("momentum_chains.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    microtask_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("microtasks.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    session_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("sessions.id", ondelete="SET NULL"), nullable=True
    )
    link_index: Mapped[int] = mapped_column(Integer, nullable=False)
    technique: Mapped[str] = mapped_column(String(40), nullable=False, default="regla_2_min")
    context_key: Mapped[str | None] = mapped_column(String(40), nullable=True)
    target_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=5)
    target_units: Mapped[str | None] = mapped_column(String(120), nullable=True)
    suggested_evidence: Mapped[str] = mapped_column(String(20), nullable=False, default="photo")
    story_caption: Mapped[str | None] = mapped_column(String(160), nullable=True)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="proposed")
    evidence_type: Mapped[str | None] = mapped_column(String(20), nullable=True)
    verified: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    heat_points: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    chain: Mapped[MomentumChain] = relationship(back_populates="links")


class ProfilePost(Base):
    """A 'Momento' on the profile: evidence of a chain link (photo, voice, video frames)."""

    __tablename__ = "profile_posts"
    __table_args__ = (
        Index("ix_profile_posts_user_created", "user_id", "created_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    chain_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("momentum_chains.id", ondelete="SET NULL"), nullable=True
    )
    link_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("chain_links.id", ondelete="SET NULL"), nullable=True
    )
    task_title: Mapped[str] = mapped_column(String(255), nullable=False)
    link_index: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    evidence_type: Mapped[str] = mapped_column(String(20), nullable=False)
    caption: Mapped[str | None] = mapped_column(String(200), nullable=True)
    detected_progress: Mapped[str | None] = mapped_column(String(320), nullable=True)
    image_base64: Mapped[str | None] = mapped_column(Text, nullable=True)
    heat_points: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    shared_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utcnow, server_default=func.now()
    )


class UserTechniqueStat(Base):
    """Per-user contextual bandit statistics: technique x (urgency phase : time bucket)."""

    __tablename__ = "user_technique_stats"
    __table_args__ = (
        UniqueConstraint(
            "user_id", "technique", "context_key", name="uq_user_technique_stats_ctx"
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    technique: Mapped[str] = mapped_column(String(40), nullable=False)
    context_key: Mapped[str] = mapped_column(String(40), nullable=False)
    shown_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    started_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    completed_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    verified_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        server_default=func.now(),
    )
