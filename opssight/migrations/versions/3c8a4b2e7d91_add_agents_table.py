"""add agents table

Revision ID: 3c8a4b2e7d91
Revises: fa26b764712c
Create Date: 2026-08-25

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "3c8a4b2e7d91"
down_revision: Union[str, Sequence[str], None] = "fa26b764712c"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "agents",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "agent_id",
            sa.String(length=200),
            nullable=False,
        ),
        sa.Column(
            "host_id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "version",
            sa.String(length=64),
            nullable=True,
        ),
        sa.Column(
            "last_config_version",
            sa.String(length=128),
            nullable=True,
        ),
        sa.Column(
            "last_seen_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["host_id"],
            ["hosts.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint(
            "id"
        ),
        sa.UniqueConstraint(
            "agent_id",
            name="uq_agents_agent_id",
        ),
    )

    op.create_index(
        "ix_agents_host_id",
        "agents",
        ["host_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_agents_host_id",
        table_name="agents",
    )

    op.drop_table(
        "agents"
    )