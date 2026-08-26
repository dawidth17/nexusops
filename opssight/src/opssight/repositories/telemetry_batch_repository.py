from datetime import datetime
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.models.telemetry_batch import TelemetryBatch


def create_telemetry_batch(
    session: Session,
    agent_record_id: UUID,
    batch_id: str,
    correlation_id: str,
    acknowledged_through_sequence: int,
    accepted_at: datetime,
) -> TelemetryBatch:
    telemetry_batch = TelemetryBatch(
        agent_record_id=agent_record_id,
        batch_id=batch_id,
        correlation_id=correlation_id,
        acknowledged_through_sequence=acknowledged_through_sequence,
        accepted_at=accepted_at,
    )

    session.add(telemetry_batch)
    session.flush()

    return telemetry_batch


def get_telemetry_batch(
    session: Session,
    agent_record_id: UUID,
    batch_id: str,
) -> TelemetryBatch | None:
    statement = select(TelemetryBatch).where(
        TelemetryBatch.agent_record_id == agent_record_id,
        TelemetryBatch.batch_id == batch_id,
    )

    return session.scalar(
        statement
    )
