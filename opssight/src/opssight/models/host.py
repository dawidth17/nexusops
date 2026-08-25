from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import Boolean, DateTime, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base

if TYPE_CHECKING:
    from opssight.models.check import Check
    from opssight.models.security_signal import SecuritySignal
    from opssight.models.telemetry import Telemetry


class Host(Base):
    __tablename__ = "hosts"

    __table_args__ = (
        UniqueConstraint(
            "name",
            name="uq_hosts_name",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
    )

    address: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
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

    checks: Mapped[list["Check"]] = relationship(
        back_populates="host",
        cascade="all, delete-orphan",
    )

    telemetry: Mapped[list["Telemetry"]] = relationship(
        back_populates="host",
        cascade="all, delete-orphan",
    )

    security_signals: Mapped[list["SecuritySignal"]] = relationship(
        back_populates="host",
        cascade="all, delete-orphan",
    )