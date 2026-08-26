from collections.abc import Generator
from datetime import UTC, datetime
from hashlib import sha256
from uuid import uuid4

import pytest
from google.protobuf.timestamp_pb2 import Timestamp
from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.alerting.engine import process_check_result
from opssight.checks.result import CheckResult
from opssight.config import settings
from opssight.correlation import resolve_telemetry_correlation_id
from opssight.database import SessionFactory
from opssight.generated.nexusops.telemetry.v1 import telemetry_pb2
from opssight.grpc.telemetry_service import process_telemetry_envelope
from opssight.models.agent import Agent
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import AlertSeverity
from opssight.models.outbox_event import OutboxEvent
from opssight.models.telemetry import Telemetry
from opssight.models.telemetry_batch import TelemetryBatch
from opssight.outbox import AlertEventType

database_test = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="database tests require a dedicated test database",
)

CORRELATION_ID = (
    "cd87135d369ad638"
    "4961ee44b67c7d2d"
    "069b1880de6e6d5f"
    "7bbadcb027375cf1"
)


@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


def build_timestamp(
    value: datetime,
) -> Timestamp:
    timestamp = Timestamp()

    timestamp.FromDatetime(
        value
    )

    return timestamp


def test_missing_telemetry_correlation_is_deterministic() -> None:
    correlation_id = resolve_telemetry_correlation_id(
        "agent-123",
        "batch-456",
        None,
    )

    expected = sha256(
        b"sentinel-agent|agent-123|batch-456"
    ).hexdigest()

    assert correlation_id == expected

    assert (
        resolve_telemetry_correlation_id(
            "agent-123",
            "batch-456",
            None,
        )
        == correlation_id
    )


@database_test
def test_correlation_id_flows_through_opssight(
    session: Session,
) -> None:
    suffix = uuid4().hex[:8]

    agent_id = f"correlation-agent-{suffix}"
    hostname = f"correlation-host-{suffix}"

    now = datetime.now(UTC)

    heartbeat = telemetry_pb2.TelemetryEnvelope(
        agent_id=agent_id,
        batch_id=f"heartbeat-{suffix}",
        sent_at=build_timestamp(
            now
        ),
        heartbeat=telemetry_pb2.Heartbeat(
            hostname=hostname,
            agent_version="0.9.0",
        ),
    )

    process_telemetry_envelope(
        heartbeat
    )

    metric_batch_id = (
        f"correlation-metrics-{suffix}"
    )

    metrics = telemetry_pb2.TelemetryEnvelope(
        agent_id=agent_id,
        batch_id=metric_batch_id,
        correlation_id=CORRELATION_ID,
        sent_at=build_timestamp(
            now
        ),
        metrics=telemetry_pb2.MetricBatch(
            records=[
                telemetry_pb2.TelemetryRecord(
                    sequence=42,
                    captured_at=build_timestamp(
                        now
                    ),
                    system=telemetry_pb2.SystemMetrics(
                        cpu_usage_percent=91.5,
                        memory_used_bytes=1_048_576,
                        filesystem_used_bytes=2_097_152,
                        uptime_seconds=3600.0,
                    ),
                )
            ]
        ),
    )

    process_telemetry_envelope(
        metrics
    )

    agent = session.scalar(
        select(Agent).where(
            Agent.agent_id
            == agent_id
        )
    )

    assert agent is not None

    telemetry_batch = session.scalar(
        select(TelemetryBatch).where(
            TelemetryBatch.agent_record_id
            == agent.id,
            TelemetryBatch.batch_id
            == metric_batch_id,
        )
    )

    assert telemetry_batch is not None

    assert (
        telemetry_batch.correlation_id
        == CORRELATION_ID
    )

    telemetry_rows = list(
        session.scalars(
            select(Telemetry).where(
                Telemetry.host_id
                == agent.host_id
            )
        ).all()
    )

    assert len(telemetry_rows) == 4

    assert all(
        row.labels[
            "correlation_id"
        ]
        == CORRELATION_ID
        for row in telemetry_rows
    )

    check = Check(
        host=agent.host,
        name=f"correlation-http-{suffix}",
        check_type="http",
        target="http://127.0.0.1/health",
    )

    rule = AlertRule(
        check=check,
        name=f"correlation-rule-{suffix}",
        severity=AlertSeverity.CRITICAL.value,
        failure_threshold=1,
    )

    session.add_all(
        [
            check,
            rule,
        ]
    )

    session.flush()

    opened_alerts = process_check_result(
        session,
        check.id,
        CheckResult(
            success=False,
            started_at=now,
            duration_ms=10.0,
            message="HTTP check failed",
            correlation_id=(
                telemetry_batch.correlation_id
            ),
        ),
    )

    assert len(opened_alerts) == 1

    alert = opened_alerts[0]

    assert (
        alert.correlation_id
        == CORRELATION_ID
    )

    opened_event = session.scalar(
        select(OutboxEvent).where(
            OutboxEvent.aggregate_id
            == alert.id,
            OutboxEvent.event_type
            == AlertEventType.OPENED.value,
        )
    )

    assert opened_event is not None

    assert (
        opened_event.correlation_id
        == CORRELATION_ID
    )

    assert (
        opened_event.event_data["correlationId"]
        == CORRELATION_ID
    )

    recovered_alerts = process_check_result(
        session,
        check.id,
        CheckResult(
            success=True,
            started_at=now,
            duration_ms=20.0,
            message="HTTP check recovered",
            correlation_id="different-recovery-correlation",
        ),
    )

    assert recovered_alerts == [
        alert
    ]

    assert (
        alert.correlation_id
        == CORRELATION_ID
    )

    recovered_event = session.scalar(
        select(OutboxEvent).where(
            OutboxEvent.aggregate_id
            == alert.id,
            OutboxEvent.event_type
            == AlertEventType.RECOVERED.value,
        )
    )

    assert recovered_event is not None

    assert (
        recovered_event.correlation_id
        == CORRELATION_ID
    )

    assert (
        recovered_event.event_data["correlationId"]
        == CORRELATION_ID
    )
