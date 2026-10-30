"""Momentum chains (domino effect), profile posts, public profiles and technique learning stats

Revision ID: 20261030_0003
Revises: 20261029_0002
Create Date: 2026-10-30 05:10:00

"""
from __future__ import annotations

from collections.abc import Sequence
from alembic import op
import sqlalchemy as sa

revision: str = "20261030_0003"
down_revision: str | None = "20261029_0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def _now() -> sa.TextClause:
    return sa.text("now()")


def upgrade() -> None:
    op.add_column("hooks", sa.Column("technique", sa.String(length=40), nullable=True))
    op.add_column("hooks", sa.Column("context_key", sa.String(length=40), nullable=True))

    op.create_table(
        "user_profiles",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("handle", sa.String(length=40), nullable=False),
        sa.Column("bio", sa.String(length=160), nullable=True),
        sa.Column("public_enabled", sa.Boolean(), server_default=sa.text("true"), nullable=False),
        sa.Column("best_daily_streak", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("best_heat", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_user_profiles_user_id", "user_profiles", ["user_id"], unique=True)
    op.create_index("ix_user_profiles_handle", "user_profiles", ["handle"], unique=True)

    op.create_table(
        "momentum_chains",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("task_id", sa.Uuid(), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.Column("links_completed", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("heat", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_link_completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("broken_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["task_id"], ["tasks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_momentum_chains_user_id", "momentum_chains", ["user_id"])
    op.create_index("ix_momentum_chains_task_id", "momentum_chains", ["task_id"])
    op.create_index("ix_momentum_chains_user_status", "momentum_chains", ["user_id", "status"])

    op.create_table(
        "chain_links",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("chain_id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("microtask_id", sa.Uuid(), nullable=False),
        sa.Column("session_id", sa.Uuid(), nullable=True),
        sa.Column("link_index", sa.Integer(), nullable=False),
        sa.Column("technique", sa.String(length=40), nullable=False),
        sa.Column("context_key", sa.String(length=40), nullable=True),
        sa.Column("target_minutes", sa.Integer(), nullable=False, server_default="5"),
        sa.Column("target_units", sa.String(length=120), nullable=True),
        sa.Column("suggested_evidence", sa.String(length=20), nullable=False),
        sa.Column("story_caption", sa.String(length=160), nullable=True),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.Column("evidence_type", sa.String(length=20), nullable=True),
        sa.Column("verified", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("heat_points", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["chain_id"], ["momentum_chains.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["microtask_id"], ["microtasks.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["session_id"], ["sessions.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("chain_id", "link_index", name="uq_chain_links_chain_index"),
    )
    op.create_index("ix_chain_links_chain_id", "chain_links", ["chain_id"])
    op.create_index("ix_chain_links_user_id", "chain_links", ["user_id"])
    op.create_index("ix_chain_links_microtask_id", "chain_links", ["microtask_id"])

    op.create_table(
        "profile_posts",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("chain_id", sa.Uuid(), nullable=True),
        sa.Column("link_id", sa.Uuid(), nullable=True),
        sa.Column("task_title", sa.String(length=255), nullable=False),
        sa.Column("link_index", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("evidence_type", sa.String(length=20), nullable=False),
        sa.Column("caption", sa.String(length=200), nullable=True),
        sa.Column("detected_progress", sa.String(length=320), nullable=True),
        sa.Column("image_base64", sa.Text(), nullable=True),
        sa.Column("heat_points", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("shared_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["chain_id"], ["momentum_chains.id"], ondelete="SET NULL"),
        sa.ForeignKeyConstraint(["link_id"], ["chain_links.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_profile_posts_user_id", "profile_posts", ["user_id"])
    op.create_index("ix_profile_posts_user_created", "profile_posts", ["user_id", "created_at"])

    op.create_table(
        "user_technique_stats",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("technique", sa.String(length=40), nullable=False),
        sa.Column("context_key", sa.String(length=40), nullable=False),
        sa.Column("shown_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("started_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("completed_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("verified_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=_now(), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "user_id", "technique", "context_key", name="uq_user_technique_stats_ctx"
        ),
    )
    op.create_index("ix_user_technique_stats_user_id", "user_technique_stats", ["user_id"])


def downgrade() -> None:
    op.drop_index("ix_user_technique_stats_user_id", table_name="user_technique_stats")
    op.drop_table("user_technique_stats")
    op.drop_index("ix_profile_posts_user_created", table_name="profile_posts")
    op.drop_index("ix_profile_posts_user_id", table_name="profile_posts")
    op.drop_table("profile_posts")
    op.drop_index("ix_chain_links_microtask_id", table_name="chain_links")
    op.drop_index("ix_chain_links_user_id", table_name="chain_links")
    op.drop_index("ix_chain_links_chain_id", table_name="chain_links")
    op.drop_table("chain_links")
    op.drop_index("ix_momentum_chains_user_status", table_name="momentum_chains")
    op.drop_index("ix_momentum_chains_task_id", table_name="momentum_chains")
    op.drop_index("ix_momentum_chains_user_id", table_name="momentum_chains")
    op.drop_table("momentum_chains")
    op.drop_index("ix_user_profiles_handle", table_name="user_profiles")
    op.drop_index("ix_user_profiles_user_id", table_name="user_profiles")
    op.drop_table("user_profiles")
    op.drop_column("hooks", "context_key")
    op.drop_column("hooks", "technique")
