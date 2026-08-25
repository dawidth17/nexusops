from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.host import Host


def create_host(
    session: Session,
    name: str,
    address: str,
) -> Host:
    host = Host(
        name=name,
        address=address,
    )

    session.add(host)
    session.flush()

    return host


def get_host_by_id(
    session: Session,
    host_id: UUID,
) -> Host | None:
    return session.get(
        Host,
        host_id,
    )


def get_host_by_name(
    session: Session,
    name: str,
) -> Host | None:
    statement = select(Host).where(
        Host.name == name
    )

    return session.scalar(
        statement
    )


def list_hosts(
    session: Session,
) -> list[Host]:
    statement = select(Host).order_by(
        Host.name
    )

    return list(
        session.scalars(statement).all()
    )