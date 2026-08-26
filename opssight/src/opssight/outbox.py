import json
import logging
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from enum import StrEnum
from typing import Any, Protocol
from uuid import uuid4

from confluent_kafka import Producer
from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.config import settings
from opssight.correlation import validate_correlation_id
from opssight.metrics import record_outbox_publication
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.enums import AlertStatus
from opssight.models.outbox_event import OutboxEvent

logger = logging.getLogger(__name__)


class AlertEventType(StrEnum):
    OPENED = "nexusops.alert.opened"
    RECOVERED = "nexusops.alert.recovered"


class EventPublisher(Protocol):
    def publish(
        self,
        topic: str,
        key: str,
        event: dict[str, Any],
    ) -> None:
        ...


PublisherFactory = Callable[
    [],
    EventPublisher,
]


@dataclass(frozen=True)
class PublicationResult:
    attempted: int
    published: int
    failed: int


class KafkaEventPublisher:
    def __init__(
        self,
        bootstrap_servers: str,
        delivery_timeout_seconds: float,
        producer: Any | None = None,
    ) -> None:
        self._delivery_timeout_seconds = (
            delivery_timeout_seconds
        )

        self._producer = (
            producer
            if producer is not None
            else Producer(
                {
                    "bootstrap.servers": bootstrap_servers,
                    "enable.idempotence": True,
                    "acks": "all",
                }
            )
        )

    def publish(
        self,
        topic: str,
        key: str,
        event: dict[str, Any],
    ) -> None:
        delivery_errors: list[str] = []

        def on_delivery(
            error: object | None,
            _message: object,
        ) -> None:
            if error is not None:
                delivery_errors.append(
                    str(error)
                )

        encoded_event = json.dumps(
            event,
            separators=(",", ":"),
            sort_keys=True,
        ).encode("utf-8")

        self._producer.produce(
            topic=topic,
            key=key.encode("utf-8"),
            value=encoded_event,
            on_delivery=on_delivery,
        )

        remaining_messages = self._producer.flush(
            self._delivery_timeout_seconds
        )

        if remaining_messages != 0:
            raise RuntimeError(
                "Kafka delivery timed out with "
                f"{remaining_messages} message(s) pending"
            )

        if delivery_errors:
            raise RuntimeError(
                "Kafka delivery failed: "
                f"{delivery_errors[0]}"
            )


def build_kafka_publisher() -> KafkaEventPublisher:
    return KafkaEventPublisher(
        bootstrap_servers=(
            settings.kafka_bootstrap_servers
        ),
        delivery_timeout_seconds=(
            settings.kafka_delivery_timeout_seconds
        ),
    )


def format_utc_timestamp(
    value: datetime,
) -> str:
    if (
        value.tzinfo is None
        or value.utcoffset() is None
    ):
        raise ValueError(
            "event timestamps must be timezone-aware"
        )

    return (
        value.astimezone(UTC)
        .isoformat()
        .replace(
            "+00:00",
            "Z",
        )
    )


def enqueue_alert_event(
    session: Session,
    alert: Alert,
    rule: AlertRule,
    event_type: AlertEventType,
) -> OutboxEvent:
    session.flush()

    if alert.id is None:
        raise ValueError(
            "alert id is required for event creation"
        )

    if rule.id is None:
        raise ValueError(
            "alert rule id is required for event creation"
        )

    if rule.check_id is None:
        raise ValueError(
            "check id is required for event creation"
        )

    correlation_id = validate_correlation_id(
        alert.correlation_id
    )

    if event_type == AlertEventType.OPENED:
        if alert.status != AlertStatus.OPEN.value:
            raise ValueError(
                "AlertOpened requires an open alert"
            )

        occurred_at = alert.opened_at
        recovered_at: str | None = None

    elif event_type == AlertEventType.RECOVERED:
        if alert.status != AlertStatus.RECOVERED.value:
            raise ValueError(
                "AlertRecovered requires a recovered alert"
            )

        if alert.recovered_at is None:
            raise ValueError(
                "AlertRecovered requires recovered_at"
            )

        occurred_at = alert.recovered_at

        recovered_at = format_utc_timestamp(
            alert.recovered_at
        )

    else:
        raise ValueError(
            f"unsupported alert event type: {event_type}"
        )

    event_id = uuid4()

    event_data: dict[str, Any] = {
        "eventId": str(event_id),
        "eventType": event_type.value,
        "schemaVersion": 1,
        "occurredAt": format_utc_timestamp(
            occurred_at
        ),
        "source": "opssight",
        "correlationId": correlation_id,
        "payload": {
            "alertId": str(alert.id),
            "alertRuleId": str(rule.id),
            "checkId": str(rule.check_id),
            "status": alert.status,
            "severity": rule.severity,
            "message": alert.message,
            "openedAt": format_utc_timestamp(
                alert.opened_at
            ),
            "recoveredAt": recovered_at,
        },
    }

    outbox_event = OutboxEvent(
        id=event_id,
        aggregate_type="alert",
        aggregate_id=alert.id,
        event_type=event_type.value,
        schema_version=1,
        occurred_at=occurred_at,
        source="opssight",
        correlation_id=correlation_id,
        topic=settings.kafka_alert_topic,
        message_key=str(alert.id),
        event_data=event_data,
    )

    session.add(
        outbox_event
    )

    session.flush()

    return outbox_event


def retry_delay_seconds(
    attempt_count: int,
) -> int:
    exponent = max(
        attempt_count - 1,
        0,
    )

    delay = (
        settings.outbox_retry_base_seconds
        * (2**exponent)
    )

    return min(
        delay,
        settings.outbox_retry_max_seconds,
    )


def publish_pending_outbox_events(
    session: Session,
    publisher_factory: PublisherFactory = (
        build_kafka_publisher
    ),
    *,
    now: datetime | None = None,
    batch_size: int | None = None,
) -> PublicationResult:
    current_time = (
        now
        if now is not None
        else datetime.now(UTC)
    )

    effective_batch_size = (
        batch_size
        if batch_size is not None
        else settings.outbox_batch_size
    )

    events = list(
        session.scalars(
            select(
                OutboxEvent
            )
            .where(
                OutboxEvent.published_at.is_(None),
                OutboxEvent.next_attempt_at
                <= current_time,
            )
            .order_by(
                OutboxEvent.created_at,
                OutboxEvent.id,
            )
            .limit(
                effective_batch_size
            )
            .with_for_update(
                skip_locked=True
            )
        ).all()
    )

    if not events:
        return PublicationResult(
            attempted=0,
            published=0,
            failed=0,
        )

    publisher = publisher_factory()

    published = 0
    failed = 0

    for event in events:
        event.attempt_count += 1
        event.last_attempt_at = current_time

        try:
            publisher.publish(
                topic=event.topic,
                key=event.message_key,
                event=event.event_data,
            )

        except Exception as error:
            failed += 1

            error_message = (
                str(error)
                or error.__class__.__name__
            )

            event.last_error = (
                error_message[:2000]
            )

            event.next_attempt_at = (
                current_time
                + timedelta(
                    seconds=retry_delay_seconds(
                        event.attempt_count
                    )
                )
            )

            record_outbox_publication(
                event_type=event.event_type,
                success=False,
            )

            logger.warning(
                "outbox_publish_failed",
                exc_info=True,
                extra={
                    "event_id": str(event.id),
                    "event_type": event.event_type,
                    "correlation_id": event.correlation_id,
                    "attempt": event.attempt_count,
                    "error": error_message,
                },
            )

        else:
            published += 1

            event.published_at = current_time
            event.last_error = None

            record_outbox_publication(
                event_type=event.event_type,
                success=True,
            )

            logger.info(
                "outbox_published",
                extra={
                    "event_id": str(event.id),
                    "event_type": event.event_type,
                    "correlation_id": event.correlation_id,
                    "attempt": event.attempt_count,
                },
            )

    session.flush()

    return PublicationResult(
        attempted=len(events),
        published=published,
        failed=failed,
    )
