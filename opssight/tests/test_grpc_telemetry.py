from datetime import UTC, datetime
from uuid import uuid4

import grpc
import pytest
from google.protobuf.timestamp_pb2 import Timestamp
from sqlalchemy import func, select

from opssight.config import settings
from opssight.correlation import (
    resolve_telemetry_correlation_id,
)
from opssight.database import SessionFactory
from opssight.generated.nexusops.telemetry.v1 import (
    telemetry_pb2,
    telemetry_pb2_grpc,
)
from opssight.grpc.security import GrpcMtlsConfiguration
from opssight.grpc.server import create_grpc_server
from opssight.models.agent import Agent
from opssight.models.telemetry import Telemetry
from opssight.models.telemetry_batch import TelemetryBatch

pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="gRPC telemetry tests require a dedicated test database",
)

CORRELATION_ID = (
    "cd87135d369ad638"
    "4961ee44b67c7d2d"
    "069b1880de6e6d5f"
    "7bbadcb027375cf1"
)


def build_timestamp(
    value: datetime,
) -> Timestamp:
    timestamp = Timestamp()

    timestamp.FromDatetime(
        value
    )

    return timestamp


def test_grpc_telemetry_delivery_and_deduplication() -> None:
    suffix = uuid4().hex[:8]

    agent_id = f"grpc-test-agent-{suffix}"
    hostname = f"grpc-test-host-{suffix}"

    heartbeat_batch_id = (
        f"heartbeat-batch-{suffix}"
    )

    metric_batch_id = (
        f"metric-batch-{suffix}"
    )

    server, port = create_grpc_server(
        host="127.0.0.1",
        port=0,
        mtls_configuration=GrpcMtlsConfiguration(
            enabled=False
        ),
    )

    server.start()

    now = datetime.now(UTC)

    heartbeat = telemetry_pb2.TelemetryEnvelope(
        agent_id=agent_id,
        batch_id=heartbeat_batch_id,
        sent_at=build_timestamp(
            now
        ),
        heartbeat=telemetry_pb2.Heartbeat(
            hostname=hostname,
            agent_version="0.1.0",
        ),
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

            assert (
                responses[0].WhichOneof("body")
                == "ack"
            )

            assert (
                responses[0].ack.batch_id
                == heartbeat_batch_id
            )

            assert (
                responses[1].WhichOneof("body")
                == "ack"
            )

            assert (
                responses[1].ack.batch_id
                == metric_batch_id
            )

            assert (
                responses[
                    1
                ].ack.acknowledged_through_sequence
                == 42
            )

            duplicate_responses = list(
                stub.StreamTelemetry(
                    iter(
                        [
                            metrics
                        ]
                    ),
                    timeout=5,
                )
            )

            assert (
                len(duplicate_responses)
                == 1
            )

            assert (
                duplicate_responses[0].ack.batch_id
                == metric_batch_id
            )

            conflicting_metrics = (
                telemetry_pb2.TelemetryEnvelope()
            )

            conflicting_metrics.CopyFrom(
                metrics
            )

            conflicting_metrics.correlation_id = (
                "different-correlation-id"
            )

            with pytest.raises(
                grpc.RpcError
            ) as error:
                list(
                    stub.StreamTelemetry(
                        iter(
                            [
                                conflicting_metrics
                            ]
                        ),
                        timeout=5,
                    )
                )

            assert (
                error.value.code()
                == grpc.StatusCode.FAILED_PRECONDITION
            )

    finally:
        server.stop(
            grace=0
        ).wait()

    with SessionFactory() as session:
        agent = session.scalar(
            select(Agent).where(
                Agent.agent_id
                == agent_id
            )
        )

        assert agent is not None

        assert (
            agent.version
            == "0.1.0"
        )

        assert (
            agent.last_seen_at
            is not None
        )

        assert (
            agent.host.name
            == hostname
        )

        telemetry_count = session.scalar(
            select(
                func.count()
            )
            .select_from(
                Telemetry
            )
            .where(
                Telemetry.host_id
                == agent.host_id
            )
        )

        batch_count = session.scalar(
            select(
                func.count()
            )
            .select_from(
                TelemetryBatch
            )
            .where(
                TelemetryBatch.agent_record_id
                == agent.id
            )
        )

        assert telemetry_count == 4
        assert batch_count == 2

        heartbeat_batch = session.scalar(
            select(
                TelemetryBatch
            ).where(
                TelemetryBatch.agent_record_id
                == agent.id,
                TelemetryBatch.batch_id
                == heartbeat_batch_id,
            )
        )

        assert heartbeat_batch is not None

        expected_heartbeat_correlation_id = (
            resolve_telemetry_correlation_id(
                agent_id,
                heartbeat_batch_id,
                None,
            )
        )

        assert (
            heartbeat_batch.correlation_id
            == expected_heartbeat_correlation_id
        )

        metric_batch = session.scalar(
            select(
                TelemetryBatch
            ).where(
                TelemetryBatch.agent_record_id
                == agent.id,
                TelemetryBatch.batch_id
                == metric_batch_id,
            )
        )

        assert metric_batch is not None

        assert (
            metric_batch.correlation_id
            == CORRELATION_ID
        )

        telemetry_rows = list(
            session.scalars(
                select(
                    Telemetry
                ).where(
                    Telemetry.host_id
                    == agent.host_id
                )
            ).all()
        )

        assert len(
            telemetry_rows
        ) == 4

        assert all(
            row.labels[
                "correlation_id"
            ]
            == CORRELATION_ID
            for row in telemetry_rows
        )
