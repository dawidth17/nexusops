"""create telemetry hypertable

Revision ID: 5ccd97084563
Revises: cb0e20320a06
Create Date: 2026-08-23 19:51:36.585954

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


revision: str = "5ccd97084563"
down_revision: Union[str, Sequence[str], None] = "cb0e20320a06"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "telemetry",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column(
            "captured_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column("host_id", sa.Uuid(), nullable=False),
        sa.Column(
            "metric_name",
            sa.String(length=100),
            nullable=False,
        ),
        sa.Column("value", sa.Double(), nullable=False),
        sa.Column(
            "unit",
            sa.String(length=32),
            nullable=True,
        ),
        sa.Column(
            "labels",
            postgresql.JSONB(astext_type=sa.Text()),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["host_id"],
            ["hosts.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint(
            "id",
            "captured_at",
        ),
    )

    op.create_index(
        "ix_telemetry_host_id_captured_at",
        "telemetry",
        ["host_id", "captured_at"],
        unique=False,
    )

    op.create_index(
        "ix_telemetry_metric_name_captured_at",
        "telemetry",
        ["metric_name", "captured_at"],
        unique=False,
    )

    op.execute(
        """
        SELECT create_hypertable(
            'telemetry',
            by_range('captured_at', INTERVAL '1 day')
        )
        """
    )

    op.execute(
        """
        SELECT add_retention_policy(
            'telemetry',
            INTERVAL '30 days'
        )
        """
    )


def downgrade() -> None:
    op.execute(
        """
        SELECT remove_retention_policy(
            'telemetry',
            if_exists => TRUE
        )
        """
    )

    op.drop_index(
        "ix_telemetry_metric_name_captured_at",
        table_name="telemetry",
    )

    op.drop_index(
        "ix_telemetry_host_id_captured_at",
        table_name="telemetry",
    )

    op.drop_table("telemetry")