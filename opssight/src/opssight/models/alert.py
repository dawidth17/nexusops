from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import DateTime, ForeignKey, Index, String, text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base
from opssight.models.enums import AlertStatus

if TYPE_CHECKING:
    from opssight.models.alert_rule import AlertRule


class Alert(Base):
    __tablename__ = "alerts"

    __table_args__ = (
        Index(
            "uq_alerts_open_alert_rule_id",
            "alert_rule_id",
            unique=True,
            postgresql_where=text("status = 'open'"),
        ),
        Index(
            "ix_alerts_correlation_id",
            "correlation_id",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    alert_rule_id: Mapped[UUID] = mapped_column(
        ForeignKey(
            "alert_rules.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default=AlertStatus.OPEN.value,
    )

    correlation_id: Mapped[str] = mapped_column(
        String(128),
        nullable=False,
    )

    message: Mapped[str] = mapped_column(
        String(1000),
        nullable=False,
    )

    opened_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    recovered_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    alert_rule: Mapped["AlertRule"] = relationship(
        back_populates="alerts",
    )
