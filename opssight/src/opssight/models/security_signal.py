from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import DateTime, ForeignKey, Index, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base

if TYPE_CHECKING:
    from opssight.models.host import Host


class SecuritySignal(Base):
    __tablename__ = "security_signals"

    __table_args__ = (
        Index(
            "ix_security_signals_type_key_observed_at",
            "signal_type",
            "signal_key",
            "observed_at",
        ),
        Index(
            "ix_security_signals_host_id_observed_at",
            "host_id",
            "observed_at",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    host_id: Mapped[UUID | None] = mapped_column(
        ForeignKey(
            "hosts.id",
            ondelete="CASCADE",
        ),
        nullable=True,
    )

    signal_type: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    signal_key: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )

    message: Mapped[str] = mapped_column(
        String(1000),
        nullable=False,
    )

    attributes: Mapped[dict[str, str]] = mapped_column(
        JSONB,
        nullable=False,
        default=dict,
    )

    observed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    host: Mapped["Host | None"] = relationship(
        back_populates="security_signals",
    )