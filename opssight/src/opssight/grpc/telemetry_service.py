import logging
from collections.abc import Iterable, Iterator
from datetime import UTC, datetime

import grpc
from google.protobuf.timestamp_pb2 import Timestamp
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from opssight.database import SessionFactory
from opssight.generated.nexusops.telemetry.v1 import (
    telemetry_pb2,
    telemetry_pb2_grpc,
)
from opssight.models.agent import Agent
from opssight.models.telemetry_batch import TelemetryBatch
from opssight.repositories.agent_repository import (
    create_agent,
    get_agent_by_agent_id,
    mark_agent_seen,
)
from opssight.repositories.host_repository import (
    create_host,
    get_host_by_name,
)
from opssight.repositories.telemetry_batch_repository import (
    create_telemetry_batch,
    get_telemetry_batch,
)
from opssight.repositories.telemetry_repository import create_telemetry

logger = logging.getLogger(__name__)


class TelemetryRequestError(Exception):
    def __init__(
        self,
        status_code: grpc.StatusCode,
        message: str,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code


def _build_timestamp(
    value: datetime,
) -> Timestamp:
    timestamp = Timestamp()
    timestamp.FromDatetime(
        value.astimezone(UTC)
    )

    return timestamp


def _build_ack(
    batch: TelemetryBatch,
) -> telemetry_pb2.AgentControl:
    return telemetry_pb2.AgentControl(
        ack=telemetry_pb2.Ack(
            batch_id=batch.batch_id,
            acknowledged_through_sequence=(
                batch.acknowledged_through_sequence
            ),
            accepted_at=_build_timestamp(
                batch.accepted_at
            ),
        )
    )


def _validate_envelope(
    envelope: telemetry_pb2.TelemetryEnvelope,
) -> str:
    agent_id = envelope.agent_id.strip()
    batch_id = envelope.batch_id.strip()

    if not agent_id:
        raise TelemetryRequestError(
            grpc.StatusCode.INVALID_ARGUMENT,
            "agent_id is required",
        )

    if len(agent_id) > 200:
        raise TelemetryRequestError(
            grpc.StatusCode.INVALID_ARGUMENT,
            "agent_id exceeds 200 characters",
        )

    if not batch_id:
        raise TelemetryRequestError(
            grpc.StatusCode.INVALID_ARGUMENT,
            "batch_id is required",
        )

    if len(batch_id) > 200:
        raise TelemetryRequestError(
            grpc.StatusCode.INVALID_ARGUMENT,
            "batch_id exceeds 200 characters",
        )

    if not envelope.HasField("sent_at"):
        raise TelemetryRequestError(
            grpc.StatusCode.INVALID_ARGUMENT,
            "sent_at is required",
        )

    envelope.sent_at.ToDatetime(
        tzinfo=UTC
    )

    body = envelope.WhichOneof("body")

    if body not in {
        "heartbeat",
        "metrics",
    }:
        raise TelemetryRequestError(
            grpc.StatusCode.INVALID_ARGUMENT,
            "telemetry envelope body is required",
        )

    if body == "heartbeat":
        hostname = envelope.heartbeat.hostname.strip()

        if not hostname:
            raise TelemetryRequestError(
                grpc.StatusCode.INVALID_ARGUMENT,
                "heartbeat hostname is required",
            )

        if len(hostname) > 200:
            raise TelemetryRequestError(
                grpc.StatusCode.INVALID_ARGUMENT,
                "heartbeat hostname exceeds 200 characters",
            )

        if len(envelope.heartbeat.agent_version) > 64:
            raise TelemetryRequestError(
                grpc.StatusCode.INVALID_ARGUMENT,
                "agent version exceeds 64 characters",
            )

    if body == "metrics":
        _validate_metric_batch(
            envelope.metrics
        )

    return body


def _validate_metric_batch(
    metric_batch: telemetry_pb2.MetricBatch,
) -> None:
    if not metric_batch.records:
        raise TelemetryRequestError(
            grpc.StatusCode.INVALID_ARGUMENT,
            "metric batch must contain at least one record",
        )

    previous_sequence: int | None = None

    for record in metric_batch.records:
        if record.sequence < 0:
            raise TelemetryRequestError(
                grpc.StatusCode.INVALID_ARGUMENT,
                "telemetry sequence cannot be negative",
            )

        if (
            previous_sequence is not None
            and record.sequence <= previous_sequence
        ):
            raise TelemetryRequestError(
                grpc.StatusCode.INVALID_ARGUMENT,
                "telemetry sequences must be strictly increasing",
            )

        previous_sequence = record.sequence

        if not record.HasField("captured_at"):
            raise TelemetryRequestError(
                grpc.StatusCode.INVALID_ARGUMENT,
                "telemetry captured_at is required",
            )

        record.captured_at.ToDatetime(
            tzinfo=UTC
        )

        if record.WhichOneof("payload") is None:
            raise TelemetryRequestError(
                grpc.StatusCode.INVALID_ARGUMENT,
                "telemetry record payload is required",
            )


def _resolve_agent(
    session: Session,
    envelope: telemetry_pb2.TelemetryEnvelope,
    body: str,
) -> Agent:
    agent_id = envelope.agent_id.strip()

    agent = get_agent_by_agent_id(
        session,
        agent_id,
    )

    if body != "heartbeat":
        if agent is None:
            raise TelemetryRequestError(
                grpc.StatusCode.FAILED_PRECONDITION,
                "agent must send a heartbeat before telemetry",
            )

        return agent

    heartbeat = envelope.heartbeat
    hostname = heartbeat.hostname.strip()

    host = get_host_by_name(
        session,
        hostname,
    )

    if agent is None:
        if host is None:
            host = create_host(
                session,
                name=hostname,
                address=hostname,
            )

        return create_agent(
            session,
            agent_id=agent_id,
            host_id=host.id,
            version=heartbeat.agent_version or None,
        )

    if agent.host.name != hostname:
        raise TelemetryRequestError(
            grpc.StatusCode.FAILED_PRECONDITION,
            "agent is already registered to a different host",
        )

    return agent


def _base_labels(
    agent: Agent,
    batch_id: str,
    sequence: int,
) -> dict[str, str]:
    return {
        "agent_id": agent.agent_id,
        "batch_id": batch_id,
        "sequence": str(sequence),
    }


def _persist_system_metrics(
    session: Session,
    agent: Agent,
    batch_id: str,
    record: telemetry_pb2.TelemetryRecord,
    captured_at: datetime,
) -> None:
    labels = _base_labels(
        agent,
        batch_id,
        record.sequence,
    )

    metrics = (
        (
            "system.cpu_usage_percent",
            record.system.cpu_usage_percent,
            "percent",
        ),
        (
            "system.memory_used_bytes",
            record.system.memory_used_bytes,
            "bytes",
        ),
        (
            "system.filesystem_used_bytes",
            record.system.filesystem_used_bytes,
            "bytes",
        ),
        (
            "system.uptime_seconds",
            record.system.uptime_seconds,
            "seconds",
        ),
    )

    for metric_name, value, unit in metrics:
        create_telemetry(
            session,
            host_id=agent.host_id,
            captured_at=captured_at,
            metric_name=metric_name,
            value=float(value),
            unit=unit,
            labels=labels,
        )


def _persist_network_snapshot(
    session: Session,
    agent: Agent,
    batch_id: str,
    record: telemetry_pb2.TelemetryRecord,
    captured_at: datetime,
) -> None:
    for interface in record.network.interfaces:
        labels = _base_labels(
            agent,
            batch_id,
            record.sequence,
        )

        labels.update(
            {
                "interface": interface.name,
                "ipv4_address": interface.ipv4_address,
                "ipv6_address": interface.ipv6_address,
            }
        )

        metrics = (
            (
                "network.rx_bytes",
                interface.rx_bytes,
                "bytes",
            ),
            (
                "network.rx_packets",
                interface.rx_packets,
                "count",
            ),
            (
                "network.rx_errors",
                interface.rx_errors,
                "count",
            ),
            (
                "network.rx_dropped",
                interface.rx_dropped,
                "count",
            ),
            (
                "network.tx_bytes",
                interface.tx_bytes,
                "bytes",
            ),
            (
                "network.tx_packets",
                interface.tx_packets,
                "count",
            ),
            (
                "network.tx_errors",
                interface.tx_errors,
                "count",
            ),
            (
                "network.tx_dropped",
                interface.tx_dropped,
                "count",
            ),
        )

        for metric_name, value, unit in metrics:
            create_telemetry(
                session,
                host_id=agent.host_id,
                captured_at=captured_at,
                metric_name=metric_name,
                value=float(value),
                unit=unit,
                labels=labels,
            )


def _persist_process_snapshot(
    session: Session,
    agent: Agent,
    batch_id: str,
    record: telemetry_pb2.TelemetryRecord,
    captured_at: datetime,
) -> None:
    for process in record.processes.processes:
        labels = _base_labels(
            agent,
            batch_id,
            record.sequence,
        )

        labels.update(
            {
                "pid": str(process.pid),
                "parent_pid": str(process.parent_pid),
                "state": process.state,
                "process_name": process.name,
            }
        )

        create_telemetry(
            session,
            host_id=agent.host_id,
            captured_at=captured_at,
            metric_name="process.resident_memory_bytes",
            value=float(process.resident_memory_bytes),
            unit="bytes",
            labels=labels,
        )


def _persist_metric_batch(
    session: Session,
    agent: Agent,
    batch_id: str,
    metric_batch: telemetry_pb2.MetricBatch,
) -> int:
    acknowledged_through_sequence = 0

    for record in metric_batch.records:
        captured_at = record.captured_at.ToDatetime(
            tzinfo=UTC
        )

        payload = record.WhichOneof(
            "payload"
        )

        if payload == "system":
            _persist_system_metrics(
                session,
                agent,
                batch_id,
                record,
                captured_at,
            )

        elif payload == "network":
            _persist_network_snapshot(
                session,
                agent,
                batch_id,
                record,
                captured_at,
            )

        elif payload == "processes":
            _persist_process_snapshot(
                session,
                agent,
                batch_id,
                record,
                captured_at,
            )

        acknowledged_through_sequence = record.sequence

    return acknowledged_through_sequence


def process_telemetry_envelope(
    envelope: telemetry_pb2.TelemetryEnvelope,
) -> telemetry_pb2.AgentControl:
    body = _validate_envelope(
        envelope
    )

    accepted_at = datetime.now(UTC)

    with SessionFactory() as session:
        try:
            agent = _resolve_agent(
                session,
                envelope,
                body,
            )

            existing_batch = get_telemetry_batch(
                session,
                agent_record_id=agent.id,
                batch_id=envelope.batch_id.strip(),
            )

            if existing_batch is not None:
                mark_agent_seen(
                    session,
                    agent,
                    seen_at=accepted_at,
                )

                session.commit()

                return _build_ack(
                    existing_batch
                )

            acknowledged_through_sequence = 0

            if body == "metrics":
                acknowledged_through_sequence = (
                    _persist_metric_batch(
                        session,
                        agent,
                        envelope.batch_id.strip(),
                        envelope.metrics,
                    )
                )

            agent_version: str | None = None

            if body == "heartbeat":
                agent_version = (
                    envelope.heartbeat.agent_version
                    or None
                )

            mark_agent_seen(
                session,
                agent,
                seen_at=accepted_at,
                version=agent_version,
            )

            batch = create_telemetry_batch(
                session,
                agent_record_id=agent.id,
                batch_id=envelope.batch_id.strip(),
                acknowledged_through_sequence=(
                    acknowledged_through_sequence
                ),
                accepted_at=accepted_at,
            )

            session.commit()

            return _build_ack(
                batch
            )

        except TelemetryRequestError:
            session.rollback()
            raise

        except SQLAlchemyError:
            session.rollback()
            logger.exception(
                "telemetry_database_error",
                extra={
                    "agent_id": envelope.agent_id,
                    "batch_id": envelope.batch_id,
                },
            )
            raise


class TelemetryService(
    telemetry_pb2_grpc.TelemetryServiceServicer
):
    def StreamTelemetry(
        self,
        request_iterator: Iterable[
            telemetry_pb2.TelemetryEnvelope
        ],
        context: grpc.ServicerContext,
    ) -> Iterator[telemetry_pb2.AgentControl]:
        for envelope in request_iterator:
            try:
                yield process_telemetry_envelope(
                    envelope
                )

            except TelemetryRequestError as exc:
                context.abort(
                    exc.status_code,
                    str(exc),
                )

            except SQLAlchemyError:
                context.abort(
                    grpc.StatusCode.INTERNAL,
                    "unable to persist telemetry",
                )