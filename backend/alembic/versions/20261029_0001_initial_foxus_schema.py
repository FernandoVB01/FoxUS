"""Initial FOXUS relational schema (12 tables)

Revision ID: 20261029_0001
Revises: None
Create Date: 2026-10-29 03:45:00

"""
from __future__ import annotations

from collections.abc import Sequence
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "20261029_0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

JSONB_COL = sa.JSON().with_variant(postgresql.JSONB(astext_type=sa.Text()), "postgresql")


def upgrade() -> None:
    # 1. users
    op.create_table(
        "users",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("display_name", sa.String(length=120), nullable=False),
        sa.Column("email", sa.String(length=255), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
    )
    op.create_index(op.f("ix_users_email"), "users", ["email"], unique=True)

    # 2. user_preferences
    op.create_table(
        "user_preferences",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("intervention_level", sa.Integer(), nullable=False),
        sa.Column("preferred_hook", sa.String(length=50), nullable=False),
        sa.Column("audio_enabled", sa.Boolean(), nullable=False),
        sa.Column("music_enabled", sa.Boolean(), nullable=False),
        sa.Column("quiet_hours_start", sa.String(length=5), nullable=True),
        sa.Column("quiet_hours_end", sa.String(length=5), nullable=True),
        sa.Column("timezone", sa.String(length=64), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "intervention_level >= 0 AND intervention_level <= 3",
            name=op.f("ck_user_preferences_intervention_level_range"),
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_user_preferences_user_id_users"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_user_preferences")),
    )
    op.create_index(
        op.f("ix_user_preferences_user_id"), "user_preferences", ["user_id"], unique=True
    )

    # 3. tasks
    op.create_table(
        "tasks",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("title", sa.String(length=255), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("source", sa.String(length=50), nullable=False),
        sa.Column("external_id", sa.String(length=255), nullable=True),
        sa.Column("deadline", sa.DateTime(timezone=True), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"], name=op.f("fk_tasks_user_id_users"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_tasks")),
    )
    op.create_index(op.f("ix_tasks_user_id"), "tasks", ["user_id"], unique=False)
    op.create_index("ix_tasks_user_status", "tasks", ["user_id", "status"], unique=False)

    # 4. microtasks
    op.create_table(
        "microtasks",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("task_id", sa.Uuid(), nullable=False),
        sa.Column("title", sa.String(length=255), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.Column("estimated_seconds", sa.Integer(), nullable=False),
        sa.Column("success_criteria", sa.String(length=500), nullable=False),
        sa.Column("model_name", sa.String(length=120), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "estimated_seconds > 0",
            name=op.f("ck_microtasks_positive_estimated_seconds"),
        ),
        sa.ForeignKeyConstraint(
            ["task_id"],
            ["tasks.id"],
            name=op.f("fk_microtasks_task_id_tasks"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_microtasks")),
    )
    op.create_index(op.f("ix_microtasks_task_id"), "microtasks", ["task_id"], unique=False)

    # 5. hooks
    op.create_table(
        "hooks",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("microtask_id", sa.Uuid(), nullable=False),
        sa.Column("hook_type", sa.String(length=50), nullable=False),
        sa.Column("message", sa.Text(), nullable=False),
        sa.Column("intensity", sa.Integer(), nullable=False),
        sa.Column("channel", sa.String(length=50), nullable=False),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "intensity >= 1 AND intensity <= 3",
            name=op.f("ck_hooks_hook_intensity_range"),
        ),
        sa.ForeignKeyConstraint(
            ["microtask_id"],
            ["microtasks.id"],
            name=op.f("fk_hooks_microtask_id_microtasks"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"], name=op.f("fk_hooks_user_id_users"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_hooks")),
    )
    op.create_index(op.f("ix_hooks_microtask_id"), "hooks", ["microtask_id"], unique=False)
    op.create_index(op.f("ix_hooks_user_id"), "hooks", ["user_id"], unique=False)
    op.create_index("ix_hooks_user_created", "hooks", ["user_id", "created_at"], unique=False)

    # 6. sessions
    op.create_table(
        "sessions",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("microtask_id", sa.Uuid(), nullable=False),
        sa.Column("hook_id", sa.Uuid(), nullable=True),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("ended_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("completion_source", sa.String(length=64), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["hook_id"], ["hooks.id"], name=op.f("fk_sessions_hook_id_hooks"), ondelete="SET NULL"
        ),
        sa.ForeignKeyConstraint(
            ["microtask_id"],
            ["microtasks.id"],
            name=op.f("fk_sessions_microtask_id_microtasks"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"], name=op.f("fk_sessions_user_id_users"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_sessions")),
    )
    op.create_index(op.f("ix_sessions_hook_id"), "sessions", ["hook_id"], unique=False)
    op.create_index(op.f("ix_sessions_microtask_id"), "sessions", ["microtask_id"], unique=False)
    op.create_index(op.f("ix_sessions_user_id"), "sessions", ["user_id"], unique=False)
    op.create_index("ix_sessions_user_status", "sessions", ["user_id", "status"], unique=False)

    # 7. session_events
    op.create_table(
        "session_events",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("session_id", sa.Uuid(), nullable=True),
        sa.Column("event_type", sa.String(length=64), nullable=False),
        sa.Column("source", sa.String(length=64), nullable=False),
        sa.Column("metadata", JSONB_COL, nullable=False),
        sa.Column(
            "occurred_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["session_id"],
            ["sessions.id"],
            name=op.f("fk_session_events_session_id_sessions"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_session_events")),
    )
    op.create_index(
        op.f("ix_session_events_event_type"), "session_events", ["event_type"], unique=False
    )
    op.create_index(
        op.f("ix_session_events_session_id"), "session_events", ["session_id"], unique=False
    )

    # 8. reward_transactions
    op.create_table(
        "reward_transactions",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("session_id", sa.Uuid(), nullable=True),
        sa.Column("reward_type", sa.String(length=64), nullable=False),
        sa.Column("xp_amount", sa.Integer(), nullable=False),
        sa.Column("idempotency_key", sa.String(length=160), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "xp_amount > 0",
            name=op.f("ck_reward_transactions_positive_xp_amount"),
        ),
        sa.ForeignKeyConstraint(
            ["session_id"],
            ["sessions.id"],
            name=op.f("fk_reward_transactions_session_id_sessions"),
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_reward_transactions_user_id_users"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_reward_transactions")),
    )
    op.create_index(
        op.f("ix_reward_transactions_idempotency_key"),
        "reward_transactions",
        ["idempotency_key"],
        unique=True,
    )
    op.create_index(
        op.f("ix_reward_transactions_session_id"),
        "reward_transactions",
        ["session_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_reward_transactions_user_id"),
        "reward_transactions",
        ["user_id"],
        unique=False,
    )

    # 9. user_hook_stats
    op.create_table(
        "user_hook_stats",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("hook_type", sa.String(length=50), nullable=False),
        sa.Column("shown_count", sa.Integer(), nullable=False),
        sa.Column("accepted_count", sa.Integer(), nullable=False),
        sa.Column("started_count", sa.Integer(), nullable=False),
        sa.Column("completed_count", sa.Integer(), nullable=False),
        sa.Column("rejected_count", sa.Integer(), nullable=False),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_user_hook_stats_user_id_users"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_user_hook_stats")),
        sa.UniqueConstraint("user_id", "hook_type", name="uq_user_hook_stats_user_hook"),
    )
    op.create_index(
        op.f("ix_user_hook_stats_user_id"), "user_hook_stats", ["user_id"], unique=False
    )

    # 10. devices
    op.create_table(
        "devices",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("device_name", sa.String(length=120), nullable=False),
        sa.Column("device_type", sa.String(length=50), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"], name=op.f("fk_devices_user_id_users"), ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_devices")),
    )
    op.create_index(op.f("ix_devices_user_id"), "devices", ["user_id"], unique=False)

    # 11. device_commands
    op.create_table(
        "device_commands",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("device_id", sa.Uuid(), nullable=False),
        sa.Column("command_type", sa.String(length=64), nullable=False),
        sa.Column("payload", JSONB_COL, nullable=False),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("acknowledged_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(
            ["device_id"],
            ["devices.id"],
            name=op.f("fk_device_commands_device_id_devices"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_device_commands")),
    )
    op.create_index(
        op.f("ix_device_commands_device_id"), "device_commands", ["device_id"], unique=False
    )
    op.create_index(
        "ix_device_commands_device_status",
        "device_commands",
        ["device_id", "status"],
        unique=False,
    )

    # 12. agent_traces
    op.create_table(
        "agent_traces",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("trace_id", sa.String(length=64), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=True),
        sa.Column("agent_name", sa.String(length=64), nullable=False),
        sa.Column("model_name", sa.String(length=120), nullable=True),
        sa.Column("duration_ms", sa.Integer(), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("input_summary", JSONB_COL, nullable=True),
        sa.Column("output_summary", JSONB_COL, nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_agent_traces_user_id_users"),
            ondelete="SET NULL",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_agent_traces")),
    )
    op.create_index(op.f("ix_agent_traces_trace_id"), "agent_traces", ["trace_id"], unique=False)
    op.create_index(op.f("ix_agent_traces_user_id"), "agent_traces", ["user_id"], unique=False)
    op.create_index(
        "ix_agent_traces_trace_created", "agent_traces", ["trace_id", "created_at"], unique=False
    )


def downgrade() -> None:
    op.drop_index("ix_agent_traces_trace_created", table_name="agent_traces")
    op.drop_index(op.f("ix_agent_traces_user_id"), table_name="agent_traces")
    op.drop_index(op.f("ix_agent_traces_trace_id"), table_name="agent_traces")
    op.drop_table("agent_traces")

    op.drop_index("ix_device_commands_device_status", table_name="device_commands")
    op.drop_index(op.f("ix_device_commands_device_id"), table_name="device_commands")
    op.drop_table("device_commands")

    op.drop_index(op.f("ix_devices_user_id"), table_name="devices")
    op.drop_table("devices")

    op.drop_index(op.f("ix_user_hook_stats_user_id"), table_name="user_hook_stats")
    op.drop_table("user_hook_stats")

    op.drop_index(op.f("ix_reward_transactions_user_id"), table_name="reward_transactions")
    op.drop_index(op.f("ix_reward_transactions_session_id"), table_name="reward_transactions")
    op.drop_index(
        op.f("ix_reward_transactions_idempotency_key"), table_name="reward_transactions"
    )
    op.drop_table("reward_transactions")

    op.drop_index(op.f("ix_session_events_session_id"), table_name="session_events")
    op.drop_index(op.f("ix_session_events_event_type"), table_name="session_events")
    op.drop_table("session_events")

    op.drop_index("ix_sessions_user_status", table_name="sessions")
    op.drop_index(op.f("ix_sessions_user_id"), table_name="sessions")
    op.drop_index(op.f("ix_sessions_microtask_id"), table_name="sessions")
    op.drop_index(op.f("ix_sessions_hook_id"), table_name="sessions")
    op.drop_table("sessions")

    op.drop_index("ix_hooks_user_created", table_name="hooks")
    op.drop_index(op.f("ix_hooks_user_id"), table_name="hooks")
    op.drop_index(op.f("ix_hooks_microtask_id"), table_name="hooks")
    op.drop_table("hooks")

    op.drop_index(op.f("ix_microtasks_task_id"), table_name="microtasks")
    op.drop_table("microtasks")

    op.drop_index("ix_tasks_user_status", table_name="tasks")
    op.drop_index(op.f("ix_tasks_user_id"), table_name="tasks")
    op.drop_table("tasks")

    op.drop_index(op.f("ix_user_preferences_user_id"), table_name="user_preferences")
    op.drop_table("user_preferences")

    op.drop_index(op.f("ix_users_email"), table_name="users")
    op.drop_table("users")
