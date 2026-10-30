"""Bounded audio and video attachments for profile moments."""
from alembic import op
import sqlalchemy as sa

revision = "20261030_0004"
down_revision = "20261030_0003"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("profile_posts", sa.Column("media_base64", sa.Text(), nullable=True))
    op.add_column("profile_posts", sa.Column("media_mime", sa.String(80), nullable=True))


def downgrade():
    op.drop_column("profile_posts", "media_mime")
    op.drop_column("profile_posts", "media_base64")
