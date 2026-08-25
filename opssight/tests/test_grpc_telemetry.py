from datetime import UTC, datetime

import grpc
import pytest
from google.protobuf.timestamp_pb2 import Timestamp
from sqlalchemy import func, select

from opssight.config import settings
from opssight.database import SessionFactory
from opssight.generated.nexusops.telemetry.v1 import (
    telemetry_pb2,
    telemetry_pb2_grpc,
)
from opssight.grpc.server import create_grpc_server
from opssight.models.agent import Agent
from opssight.models.telemetry import Telemetry
from opssight.models.telemetry_batch import TelemetryBatch

pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="gRPC telemetry tests require a dedicated test database",
)


def build_timestamp(
    value: datetime,
) -> Timestamp:
    timestamp = Timestamp()
    timestamp.FromDatetime(value)

    return timestamp


def test_grpc_telemetry_delivery_and_deduplication() -> None:
    server, port = create_grpc_server(
        host="127.0.0.1",
        port=0,
    )

    server.start()

    now = datetime.now(UTC)

    heartbeat = telemetry_pb2.TelemetryEnvelope(
        agent_id="grpc-test-agent",
        batch_id="heartbeat-batch-1",
        sent_at=build_timestamp(now),
        heartbeat=telemetry_pb2.Heartbeat(
            hostname="grpc-test-host",
            agent_version="0.1.0",
        ),
    )

    metrics = telemetry_pb2.TelemetryEnvelope(
        agent_id="grpc-test-agent",
        batch_id="metric-batch-1",
        sent_at=build_timestamp(now),
        metrics=telemetry_pb2.MetricBatch(
            records=[
                telemetry_pb2.TelemetryRecord(
                    sequence=42,
                    captured_at=build_timestamp(now),
                    system=telemetry_pb2.SystemMetrics(
                        cpu_usage_percent=37.5,
                        memory_used_bytes=1_048_576,
                        filesystem_used_bytes=2_097_152,
                        uptime_seconds=3600.0,
                    ),
                )
            ]
        ),
    )

    try:
        with grpc.insecure_channel(
            f"127.0.0.1:{port}"
        ) as channel:
            grpc.channel_ready_future(
                channel
            ).result(
                timeout=5
            )

            stub = telemetry_pb2_grpc.TelemetryServiceStub(
                channel
            )

            responses = list(
                stub.StreamTelemetry(
                    iter(
                        [
                            heartbeat,
                            metrics,
                        ]
                    ),
                    timeout=5,
                )
            )

            assert len(responses) == 2

            assert responses[0].WhichOneof("body") == "ack"
            assert responses[0].ack.batch_id == "heartbeat-batch-1"

            assert responses[1].WhichOneof("body") == "ack"
            assert responses[1].ack.batch_id == "metric-batch-1"

            assert (
                responses[1].ack.acknowledged_through_sequence
                == 42
            )

            duplicate_responses = list(
                stub.StreamTelemetry(
                    iter([metrics]),
                    timeout=5,
                )
            )

            assert len(duplicate_responses) == 1

            assert (
                duplicate_responses[0].ack.batch_id
                == "metric-batch-1"
            )

            assert (
                duplicate_responses[
                    0
                ].ack.acknowledged_through_sequence
                == 42
            )

    finally:
        server.stop(
            grace=0
        ).wait()

    with SessionFactory() as session:
        agent = session.scalar(
            select(Agent).where(
                Agent.agent_id == "grpc-test-agent"
            )
        )

        assert agent is not None
        assert agent.version == "0.1.0"
        assert agent.last_seen_at is not None
        assert agent.host.name == "grpc-test-host"

        telemetry_count = session.scalar(
            select(
                func.count()
            )
            .select_from(Telemetry)
            .where(
                Telemetry.host_id == agent.host_id
            )
        )

        batch_count = session.scalar(
            select(
                func.count()
            )
            .select_from(TelemetryBatch)
            .where(
                TelemetryBatch.agent_record_id == agent.id
            )
        )

        assert telemetry_count == 4
        assert batch_count == 2