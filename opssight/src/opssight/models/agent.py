from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import DateTime, ForeignKey, Index, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base

if TYPE_CHECKING:
    from opssight.models.host import Host


class Agent(Base):
    __tablename__ = "agents"

    __table_args__ = (
        UniqueConstraint(
            "agent_id",
            name="uq_agents_agent_id",
        ),
        Index(
            "ix_agents_host_id",
            "host_id",
        ),
    )

    id: Mapped[UUID] = mapped_column(
        primary_key=True,
        default=uuid4,
    )

    agent_id: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
    )

    host_id: Mapped[UUID] = mapped_column(
        ForeignKey(
            "hosts.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )

    version: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
    )

    last_config_version: Mapped[str | None] = mapped_column(
        String(128),
        nullable=True,
    )

    last_seen_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    host: Mapped["Host"] = relationship(
        back_populates="agents",
    )