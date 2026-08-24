from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.security_finding import SecurityFinding


def get_security_finding_by_id(
    session: Session,
    finding_id: UUID,
) -> SecurityFinding | None:
    return session.get(
        SecurityFinding,
        finding_id,
    )


def list_security_findings(
    session: Session,
) -> list[SecurityFinding]:
    statement = select(
        SecurityFinding
    ).order_by(
        SecurityFinding.last_seen_at.desc(),
        SecurityFinding.id,
    )

    return list(
        session.scalars(statement).all()
    )