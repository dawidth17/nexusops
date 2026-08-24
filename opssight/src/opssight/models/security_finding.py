from datetime import datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base
from opssight.models.enums import SecurityFindingStatus


if TYPE_CHECKING:
    from opssight.models.runbook import Runbook
    from opssight.models.security_rule import SecurityRule


class SecurityFinding(Base):
    __tablename__ = "security_findings"

    __table_args__ = (
        CheckConstraint(
            "occurrence_count > 0",
            name="ck_security_findings_occurrence_count_positive",
        ),
        Index(
            "uq_security_findings_open_rule_signal_key",
            "security_rule_id",
            "signal_key",
            unique=True,
            postgresql_where=text("status = 'open'"),
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    security_rule_id: Mapped[UUID] = mapped_column(
        ForeignKey(
            "security_rules.id",
            ondelete="RESTRICT",
        ),
        nullable=False,
        index=True,
    )

    runbook_id: Mapped[UUID] = mapped_column(
        ForeignKey(
            "runbooks.id",
            ondelete="RESTRICT",
        ),
        nullable=False,
        index=True,
    )

    signal_key: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )

    severity: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )

    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default=SecurityFindingStatus.OPEN.value,
    )

    summary: Mapped[str] = mapped_column(
        String(1000),
        nullable=False,
    )

    occurrence_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
    )

    first_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    last_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    resolved_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    security_rule: Mapped["SecurityRule"] = relationship(
        back_populates="findings",
    )

    runbook: Mapped["Runbook"] = relationship(
        back_populates="security_findings",
    )