from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from sqlalchemy import DateTime, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from opssight.models.base import Base

if TYPE_CHECKING:
    from opssight.models.security_finding import SecurityFinding
    from opssight.models.security_rule import SecurityRule


class Runbook(Base):
    __tablename__ = "runbooks"

    __table_args__ = (
        UniqueConstraint(
            "name",
            name="uq_runbooks_name",
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

    description: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    instructions: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    security_rules: Mapped[list["SecurityRule"]] = relationship(
        back_populates="runbook",
    )

    security_findings: Mapped[list["SecurityFinding"]] = relationship(
        back_populates="runbook",
    )