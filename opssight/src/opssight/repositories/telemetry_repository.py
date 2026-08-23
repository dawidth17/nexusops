from datetime import datetime
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.telemetry import Telemetry


def create_telemetry(
    session: Session,
    host_id: UUID,
    captured_at: datetime,
    metric_name: str,
    value: float,
    unit: str | None = None,
    labels: dict[str, str] | None = None,
) -> Telemetry:
    telemetry = Telemetry(
        host_id=host_id,
        captured_at=captured_at,
        metric_name=metric_name,
        value=value,
        unit=unit,
        labels=labels or {},
    )

    session.add(telemetry)
    session.flush()

    return telemetry


def list_telemetry_by_time_range(
    session: Session,
    host_id: UUID,
    metric_name: str,
    start_time: datetime,
    end_time: datetime,
) -> list[Telemetry]:
    statement = (
        select(Telemetry)
        .where(
            Telemetry.host_id == host_id,
            Telemetry.metric_name == metric_name,
            Telemetry.captured_at >= start_time,
            Telemetry.captured_at < end_time,
        )
        .order_by(Telemetry.captured_at)
    )

    return list(session.scalars(statement).all())