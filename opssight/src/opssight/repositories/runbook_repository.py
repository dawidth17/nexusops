from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.runbook import Runbook


def create_runbook(
    session: Session,
    name: str,
    description: str,
    instructions: str,
) -> Runbook:
    runbook = Runbook(
        name=name,
        description=description,
        instructions=instructions,
    )

    session.add(runbook)
    session.flush()

    return runbook


def get_runbook_by_id(
    session: Session,
    runbook_id: UUID,
) -> Runbook | None:
    return session.get(
        Runbook,
        runbook_id,
    )


def list_runbooks(
    session: Session,
) -> list[Runbook]:
    statement = select(
        Runbook
    ).order_by(
        Runbook.name,
        Runbook.id,
    )

    return list(
        session.scalars(statement).all()
    )