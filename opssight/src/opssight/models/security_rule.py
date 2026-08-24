from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base
from opssight.models.enums import AlertSeverity


if TYPE_CHECKING:
    from opssight.models.runbook import Runbook
    from opssight.models.security_finding import SecurityFinding


class SecurityRule(Base):
    __tablename__ = "security_rules"

    __table_args__ = (
        CheckConstraint(
            "threshold > 0",
            name="ck_security_rules_threshold_positive",
        ),
        CheckConstraint(
            "window_seconds > 0",
            name="ck_security_rules_window_seconds_positive",
        ),
        UniqueConstraint(
            "name",
            name="uq_security_rules_name",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    runbook_id: Mapped[UUID] = mapped_column(
        ForeignKey(
            "runbooks.id",
            ondelete="RESTRICT",
        ),
        nullable=False,
        index=True,
    )

    name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
    )

    signal_type: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        index=True,
    )

    severity: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default=AlertSeverity.WARNING.value,
    )

    threshold: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
    )

    window_seconds: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=300,
    )

    enabled: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    runbook: Mapped["Runbook"] = relationship(
        back_populates="security_rules",
    )

    findings: Mapped[list["SecurityFinding"]] = relationship(
        back_populates="security_rule",
        cascade="all, delete-orphan",
    )