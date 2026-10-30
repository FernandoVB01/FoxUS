"""Add task pressure consent flag and Google Tasks/Calendar external integrations table

Revision ID: 20261029_0002
Revises: 20261029_0001
Create Date: 2026-10-29 10:10:00

"""
from __future__ import annotations

from collections.abc import Sequence
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "20261029_0002"
down_revision: str | None = "20261029_0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

JSONB_COL = sa.JSON().with_variant(postgresql.JSONB(astext_type=sa.Text()), "postgresql")


def upgrade() -> None:
    op.add_column(
        "tasks",
        sa.Column(
            "needs_pressure",
            sa.Boolean(),
            server_default=sa.text("false"),
            nullable=False,
        ),
    )

    op.create_table(
        "external_integrations",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("provider", sa.String(length=40), nullable=False),
        sa.Column("auth_mode", sa.String(length=32), nullable=False),
        sa.Column("connected_email", sa.String(length=255), nullable=True),
        sa.Column("access_token", sa.Text(), nullable=True),
        sa.Column("refresh_token", sa.Text(), nullable=True),
        sa.Column("token_expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("calendar_ical_url", sa.Text(), nullable=True),
        sa.Column("cached_events_json", JSONB_COL, nullable=False),
        sa.Column("last_synced_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "user_id",
            "provider",
            name="uq_external_integrations_user_provider",
        ),
    )
    op.create_index(
        "ix_external_integrations_user_id",
        "external_integrations",
        ["user_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_external_integrations_user_id", table_name="external_integrations")
    op.drop_table("external_integrations")
    op.drop_column("tasks", "needs_pressure")
