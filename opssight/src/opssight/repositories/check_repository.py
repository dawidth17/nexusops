from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.check import Check


def create_check(
    session: Session,
    host_id: UUID,
    name: str,
    check_type: str,
    target: str,
    interval_seconds: int,
    timeout_seconds: int,
) -> Check:
    check = Check(
        host_id=host_id,
        name=name,
        check_type=check_type,
        target=target,
        interval_seconds=interval_seconds,
        timeout_seconds=timeout_seconds,
    )

    session.add(check)
    session.flush()

    return check


def get_check_by_id(
    session: Session,
    check_id: UUID,
) -> Check | None:
    return session.get(
        Check,
        check_id,
    )


def list_checks(
    session: Session,
) -> list[Check]:
    statement = select(Check).order_by(
        Check.name,
        Check.id,
    )

    return list(
        session.scalars(statement).all()
    )


def list_checks_by_host(
    session: Session,
    host_id: UUID,
) -> list[Check]:
    statement = (
        select(Check)
        .where(
            Check.host_id == host_id
        )
        .order_by(
            Check.name,
            Check.id,
        )
    )

    return list(
        session.scalars(statement).all()
    )