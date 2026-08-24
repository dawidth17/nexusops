"""add check scheduling state

Revision ID: deb1794490c6
Revises: 6dbf7ac2a265
Create Date: 2026-08-24 08:50:58.547748

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "deb1794490c6"
down_revision: Union[str, Sequence[str], None] = "6dbf7ac2a265"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "checks",
        sa.Column(
            "next_run_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
    )

    op.execute(
        """
        UPDATE checks
        SET next_run_at = CURRENT_TIMESTAMP
        WHERE next_run_at IS NULL
        """
    )

    op.alter_column(
        "checks",
        "next_run_at",
        nullable=False,
    )

    op.create_index(
        "ix_checks_next_run_at",
        "checks",
        ["next_run_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_checks_next_run_at",
        table_name="checks",
    )

    op.drop_column(
        "checks",
        "next_run_at",
    )