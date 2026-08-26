"""add outbox events table

Revision ID: d4f2a8b7c901
Revises: a9d2f6c1b304
Create Date: 2026-08-26

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


revision: str = "d4f2a8b7c901"
down_revision: Union[str, Sequence[str], None] = "a9d2f6c1b304"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "outbox_events",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "aggregate_type",
            sa.String(length=50),
            nullable=False,
        ),
        sa.Column(
            "aggregate_id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "event_type",
            sa.String(length=200),
            nullable=False,
        ),
        sa.Column(
            "schema_version",
            sa.Integer(),
            nullable=False,
        ),
        sa.Column(
            "occurred_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "source",
            sa.String(length=64),
            nullable=False,
        ),
        sa.Column(
            "correlation_id",
            sa.String(length=128),
            nullable=False,
        ),
        sa.Column(
            "topic",
            sa.String(length=249),
            nullable=False,
        ),
        sa.Column(
            "message_key",
            sa.String(length=255),
            nullable=False,
        ),
        sa.Column(
            "event_data",
            postgresql.JSONB(
                astext_type=sa.Text(),
            ),
            nullable=False,
        ),
        sa.Column(
            "attempt_count",
            sa.Integer(),
            nullable=False,
        ),
        sa.Column(
            "next_attempt_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "last_attempt_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
        sa.Column(
            "last_error",
            sa.Text(),
            nullable=True,
        ),
        sa.Column(
            "published_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.PrimaryKeyConstraint(
            "id",
        ),
    )

    op.create_index(
        "ix_outbox_events_aggregate_id",
        "outbox_events",
        [
            "aggregate_id",
        ],
        unique=False,
    )

    op.create_index(
        "ix_outbox_events_pending",
        "outbox_events",
        [
            "next_attempt_at",
        ],
        unique=False,
        postgresql_where=sa.text(
            "published_at IS NULL"
        ),
    )


def downgrade() -> None:
    op.drop_index(
        "ix_outbox_events_pending",
        table_name="outbox_events",
    )

    op.drop_index(
        "ix_outbox_events_aggregate_id",
        table_name="outbox_events",
    )

    op.drop_table(
        "outbox_events"
    )