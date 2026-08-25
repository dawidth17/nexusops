from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base
from opssight.models.enums import AlertSeverity

if TYPE_CHECKING:
    from opssight.models.alert import Alert
    from opssight.models.check import Check


class AlertRule(Base):
    __tablename__ = "alert_rules"

    __table_args__ = (
        CheckConstraint(
            "failure_threshold > 0",
            name="ck_alert_rules_failure_threshold_positive",
        ),
        UniqueConstraint(
            "check_id",
            "name",
            name="uq_alert_rules_check_id_name",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    check_id: Mapped[UUID] = mapped_column(
        ForeignKey("checks.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
    )

    severity: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default=AlertSeverity.WARNING.value,
    )

    failure_threshold: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
    )

    enabled: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    check: Mapped["Check"] = relationship(
        back_populates="alert_rules",
    )

    alerts: Mapped[list["Alert"]] = relationship(
        back_populates="alert_rule",
        cascade="all, delete-orphan",
    )