"""add security signal schema

Revision ID: fa26b764712c
Revises: deb1794490c6
Create Date: 2026-08-24 10:33:44.059964

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


revision: str = "fa26b764712c"
down_revision: Union[str, Sequence[str], None] = "deb1794490c6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "runbooks",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "name",
            sa.String(length=200),
            nullable=False,
        ),
        sa.Column(
            "description",
            sa.Text(),
            nullable=False,
        ),
        sa.Column(
            "instructions",
            sa.Text(),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "name",
            name="uq_runbooks_name",
        ),
    )

    op.create_table(
        "security_rules",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "runbook_id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "name",
            sa.String(length=200),
            nullable=False,
        ),
        sa.Column(
            "signal_type",
            sa.String(length=100),
            nullable=False,
        ),
        sa.Column(
            "severity",
            sa.String(length=20),
            nullable=False,
        ),
        sa.Column(
            "threshold",
            sa.Integer(),
            nullable=False,
        ),
        sa.Column(
            "window_seconds",
            sa.Integer(),
            nullable=False,
        ),
        sa.Column(
            "enabled",
            sa.Boolean(),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.CheckConstraint(
            "threshold > 0",
            name="ck_security_rules_threshold_positive",
        ),
        sa.CheckConstraint(
            "window_seconds > 0",
            name="ck_security_rules_window_seconds_positive",
        ),
        sa.ForeignKeyConstraint(
            ["runbook_id"],
            ["runbooks.id"],
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "name",
            name="uq_security_rules_name",
        ),
    )

    op.create_index(
        "ix_security_rules_runbook_id",
        "security_rules",
        ["runbook_id"],
        unique=False,
    )

    op.create_index(
        "ix_security_rules_signal_type",
        "security_rules",
        ["signal_type"],
        unique=False,
    )

    op.create_table(
        "security_signals",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "host_id",
            sa.Uuid(),
            nullable=True,
        ),
        sa.Column(
            "signal_type",
            sa.String(length=100),
            nullable=False,
        ),
        sa.Column(
            "signal_key",
            sa.String(length=255),
            nullable=False,
        ),
        sa.Column(
            "message",
            sa.String(length=1000),
            nullable=False,
        ),
        sa.Column(
            "attributes",
            postgresql.JSONB(
                astext_type=sa.Text(),
            ),
            nullable=False,
        ),
        sa.Column(
            "observed_at",
            sa.DateTime(timezone=True),
            nullable=False,
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
        sa.PrimaryKeyConstraint("id"),
    )

    op.create_index(
        "ix_security_signals_host_id_observed_at",
        "security_signals",
        ["host_id", "observed_at"],
        unique=False,
    )

    op.create_index(
        "ix_security_signals_type_key_observed_at",
        "security_signals",
        [
            "signal_type",
            "signal_key",
            "observed_at",
        ],
        unique=False,
    )

    op.create_table(
        "security_findings",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "security_rule_id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "runbook_id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "signal_key",
            sa.String(length=255),
            nullable=False,
        ),
        sa.Column(
            "severity",
            sa.String(length=20),
            nullable=False,
        ),
        sa.Column(
            "status",
            sa.String(length=20),
            nullable=False,
        ),
        sa.Column(
            "summary",
            sa.String(length=1000),
            nullable=False,
        ),
        sa.Column(
            "occurrence_count",
            sa.Integer(),
            nullable=False,
        ),
        sa.Column(
            "first_seen_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "last_seen_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "resolved_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
        sa.CheckConstraint(
            "occurrence_count > 0",
            name="ck_security_findings_occurrence_count_positive",
        ),
        sa.ForeignKeyConstraint(
            ["runbook_id"],
            ["runbooks.id"],
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["security_rule_id"],
            ["security_rules.id"],
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id"),
    )

    op.create_index(
        "ix_security_findings_runbook_id",
        "security_findings",
        ["runbook_id"],
        unique=False,
    )

    op.create_index(
        "ix_security_findings_security_rule_id",
        "security_findings",
        ["security_rule_id"],
        unique=False,
    )

    op.create_index(
        "uq_security_findings_open_rule_signal_key",
        "security_findings",
        [
            "security_rule_id",
            "signal_key",
        ],
        unique=True,
        postgresql_where=sa.text(
            "status = 'open'"
        ),
    )


def downgrade() -> None:
    op.drop_index(
        "uq_security_findings_open_rule_signal_key",
        table_name="security_findings",
    )

    op.drop_index(
        "ix_security_findings_security_rule_id",
        table_name="security_findings",
    )

    op.drop_index(
        "ix_security_findings_runbook_id",
        table_name="security_findings",
    )

    op.drop_table(
        "security_findings"
    )

    op.drop_index(
        "ix_security_signals_type_key_observed_at",
        table_name="security_signals",
    )

    op.drop_index(
        "ix_security_signals_host_id_observed_at",
        table_name="security_signals",
    )

    op.drop_table(
        "security_signals"
    )

    op.drop_index(
        "ix_security_rules_signal_type",
        table_name="security_rules",
    )

    op.drop_index(
        "ix_security_rules_runbook_id",
        table_name="security_rules",
    )

    op.drop_table(
        "security_rules"
    )

    op.drop_table(
        "runbooks"
    )