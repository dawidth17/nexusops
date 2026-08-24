from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.alert import Alert


def get_alert_by_id(
    session: Session,
    alert_id: UUID,
) -> Alert | None:
    return session.get(
        Alert,
        alert_id,
    )


def list_alerts(
    session: Session,
) -> list[Alert]:
    statement = select(Alert).order_by(
        Alert.opened_at.desc(),
        Alert.id,
    )

    return list(
        session.scalars(statement).all()
    )