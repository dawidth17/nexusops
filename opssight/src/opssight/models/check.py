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


if TYPE_CHECKING:
    from opssight.models.alert_rule import AlertRule
    from opssight.models.host import Host


class Check(Base):
    __tablename__ = "checks"

    __table_args__ = (
        CheckConstraint(
            "interval_seconds > 0",
            name="ck_checks_interval_seconds_positive",
        ),
        CheckConstraint(
            "timeout_seconds > 0",
            name="ck_checks_timeout_seconds_positive",
        ),
        UniqueConstraint(
            "host_id",
            "name",
            name="uq_checks_host_id_name",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    host_id: Mapped[UUID] = mapped_column(
        ForeignKey("hosts.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
    )

    check_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
    )

    target: Mapped[str] = mapped_column(
        String(500),
        nullable=False,
    )

    interval_seconds: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=60,
    )

    timeout_seconds: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=5,
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

    host: Mapped["Host"] = relationship(
        back_populates="checks",
    )

    alert_rules: Mapped[list["AlertRule"]] = relationship(
        back_populates="check",
        cascade="all, delete-orphan",
    )