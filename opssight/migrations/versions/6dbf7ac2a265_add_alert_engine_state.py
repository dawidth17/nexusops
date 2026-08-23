"""add alert engine state

Revision ID: 6dbf7ac2a265
Revises: 5ccd97084563
Create Date: 2026-08-23 21:47:18.997809

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "6dbf7ac2a265"
down_revision: Union[str, Sequence[str], None] = "5ccd97084563"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "checks",
        sa.Column(
            "consecutive_failures",
            sa.Integer(),
            nullable=False,
            server_default=sa.text("0"),
        ),
    )

    op.create_check_constraint(
        "ck_checks_consecutive_failures_non_negative",
        "checks",
        "consecutive_failures >= 0",
    )

    op.alter_column(
        "checks",
        "consecutive_failures",
        server_default=None,
    )

    op.create_index(
        "uq_alerts_open_alert_rule_id",
        "alerts",
        ["alert_rule_id"],
        unique=True,
        postgresql_where=sa.text("status = 'open'"),
    )


def downgrade() -> None:
    op.drop_index(
        "uq_alerts_open_alert_rule_id",
        table_name="alerts",
    )

    op.drop_constraint(
        "ck_checks_consecutive_failures_non_negative",
        "checks",
        type_="check",
    )

    op.drop_column(
        "checks",
        "consecutive_failures",
    )