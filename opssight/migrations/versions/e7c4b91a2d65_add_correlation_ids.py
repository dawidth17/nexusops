"""add correlation ids

Revision ID: e7c4b91a2d65
Revises: d4f2a8b7c901
Create Date: 2026-08-26

"""

from hashlib import sha256
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "e7c4b91a2d65"
down_revision: Union[str, Sequence[str], None] = "d4f2a8b7c901"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "telemetry_batches",
        sa.Column(
            "correlation_id",
            sa.String(length=128),
            nullable=True,
        ),
    )

    op.add_column(
        "alerts",
        sa.Column(
            "correlation_id",
            sa.String(length=128),
            nullable=True,
        ),
    )

    connection = op.get_bind()

    batches = (
        connection.execute(
            sa.text(
                """
                SELECT
                    telemetry_batches.id,
                    telemetry_batches.batch_id,
                    agents.agent_id
                FROM telemetry_batches
                JOIN agents
                  ON agents.id = telemetry_batches.agent_record_id
                """
            )
        )
        .mappings()
        .all()
    )

    for batch in batches:
        material = (
            "sentinel-agent|"
            f"{batch['agent_id']}|"
            f"{batch['batch_id']}"
        )

        correlation_id = sha256(
            material.encode(
                "utf-8"
            )
        ).hexdigest()

        connection.execute(
            sa.text(
                """
                UPDATE telemetry_batches
                SET correlation_id = :correlation_id
                WHERE id = :record_id
                """
            ),
            {
                "correlation_id": correlation_id,
                "record_id": batch["id"],
            },
        )

    connection.execute(
        sa.text(
            """
            UPDATE alerts
            SET correlation_id = CAST(id AS VARCHAR)
            WHERE correlation_id IS NULL
            """
        )
    )

    op.alter_column(
        "telemetry_batches",
        "correlation_id",
        existing_type=sa.String(length=128),
        nullable=False,
    )

    op.alter_column(
        "alerts",
        "correlation_id",
        existing_type=sa.String(length=128),
        nullable=False,
    )

    op.create_index(
        "ix_telemetry_batches_correlation_id",
        "telemetry_batches",
        [
            "correlation_id",
        ],
        unique=False,
    )

    op.create_index(
        "ix_alerts_correlation_id",
        "alerts",
        [
            "correlation_id",
        ],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_alerts_correlation_id",
        table_name="alerts",
    )

    op.drop_index(
        "ix_telemetry_batches_correlation_id",
        table_name="telemetry_batches",
    )

    op.drop_column(
        "alerts",
        "correlation_id",
    )

    op.drop_column(
        "telemetry_batches",
        "correlation_id",
    )
