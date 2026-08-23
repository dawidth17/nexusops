from datetime import datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import DateTime, Double, ForeignKey, Index, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base


if TYPE_CHECKING:
    from opssight.models.host import Host


class Telemetry(Base):
    __tablename__ = "telemetry"

    __table_args__ = (
        Index(
            "ix_telemetry_host_id_captured_at",
            "host_id",
            "captured_at",
        ),
        Index(
            "ix_telemetry_metric_name_captured_at",
            "metric_name",
            "captured_at",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    captured_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        primary_key=True,
        nullable=False,
    )

    host_id: Mapped[UUID] = mapped_column(
        ForeignKey("hosts.id", ondelete="CASCADE"),
        nullable=False,
    )

    metric_name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    value: Mapped[float] = mapped_column(
        Double,
        nullable=False,
    )

    unit: Mapped[str | None] = mapped_column(
        String(32),
        nullable=True,
    )

    labels: Mapped[dict[str, str]] = mapped_column(
        JSONB,
        nullable=False,
        default=dict,
    )

    host: Mapped["Host"] = relationship(
        back_populates="telemetry",
    )