"""add telemetry batches table

Revision ID: a9d2f6c1b304
Revises: 3c8a4b2e7d91
Create Date: 2026-08-25

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "a9d2f6c1b304"
down_revision: Union[str, Sequence[str], None] = "3c8a4b2e7d91"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "telemetry_batches",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "agent_record_id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "batch_id",
            sa.String(length=200),
            nullable=False,
        ),
        sa.Column(
            "acknowledged_through_sequence",
            sa.BigInteger(),
            nullable=False,
        ),
        sa.Column(
            "accepted_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["agent_record_id"],
            ["agents.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint(
            "id",
        ),
        sa.UniqueConstraint(
            "agent_record_id",
            "batch_id",
            name="uq_telemetry_batches_agent_batch",
        ),
    )

    op.create_index(
        "ix_telemetry_batches_agent_accepted_at",
        "telemetry_batches",
        ["agent_record_id", "accepted_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_telemetry_batches_agent_accepted_at",
        table_name="telemetry_batches",
    )

    op.drop_table(
        "telemetry_batches",
    )