from datetime import UTC, datetime
from uuid import UUID, uuid4

from sqlalchemy import BigInteger, DateTime, ForeignKey, Index, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from opssight.models.base import Base


class TelemetryBatch(Base):
    __tablename__ = "telemetry_batches"

    __table_args__ = (
        UniqueConstraint(
            "agent_record_id",
            "batch_id",
            name="uq_telemetry_batches_agent_batch",
        ),
        Index(
            "ix_telemetry_batches_agent_accepted_at",
            "agent_record_id",
            "accepted_at",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    agent_record_id: Mapped[UUID] = mapped_column(
        ForeignKey(
            "agents.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )

    batch_id: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
    )

    acknowledged_through_sequence: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    accepted_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )