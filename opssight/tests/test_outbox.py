import json
from collections.abc import Generator
from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

import pytest
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from opssight.alerting.engine import process_check_result
from opssight.checks.result import CheckResult
from opssight.config import settings
from opssight.database import SessionFactory
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import AlertSeverity
from opssight.models.host import Host
from opssight.models.outbox_event import OutboxEvent
from opssight.outbox import (
    AlertEventType,
    KafkaEventPublisher,
    publish_pending_outbox_events,
)


database_test = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="database tests require a dedicated test database",
)


@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        database_session.execute(
            delete(OutboxEvent)
        )
        database_session.flush()

        yield database_session

        database_session.rollback()


def make_result(
    success: bool,
    message: str,
) -> CheckResult:
    return CheckResult(
        success=success,
        started_at=datetime.now(UTC),
        duration_ms=10.0,
        message=message,
    )


def create_alert_rule(
    session: Session,
) -> tuple[Check, AlertRule]:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"outbox-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="outbox-http-check",
        check_type="http",
        target="http://127.0.0.1/health",
    )

    rule = AlertRule(
        check=check,
        name="outbox-alert-rule",
        severity=AlertSeverity.CRITICAL.value,
        failure_threshold=1,
    )

    session.add(host)
    session.flush()

    return check, rule


class RecordingPublisher:
    def __init__(
        self,
        failures_remaining: int = 0,
    ) -> None:
        self.failures_remaining = failures_remaining
        self.messages: list[
            tuple[
                str,
                str,
                dict[str, Any],
            ]
        ] = []

    def publish(
        self,
        topic: str,
        key: str,
        event: dict[str, Any],
    ) -> None:
        if self.failures_remaining > 0:
            self.failures_remaining -= 1

            raise RuntimeError(
                "Kafka unavailable"
            )

        self.messages.append(
            (
                topic,
                key,
                event,
            )
        )


class FakeConfluentProducer:
    def __init__(
        self,
        *,
        delivery_error: object | None = None,
        remaining_messages: int = 0,
    ) -> None:
        self.delivery_error = delivery_error
        self.remaining_messages = remaining_messages

        self.topic: str | None = None
        self.key: bytes | None = None
        self.value: bytes | None = None
        self.flush_timeout: float | None = None

    def produce(
        self,
        *,
        topic: str,
        key: bytes,
        value: bytes,
        on_delivery,
    ) -> None:
        self.topic = topic
        self.key = key
        self.value = value

        on_delivery(
            self.delivery_error,
            object(),
        )

    def flush(
        self,
        timeout: float,
    ) -> int:
        self.flush_timeout = timeout

        return self.remaining_messages


def create_pending_event(
    session: Session,
    now: datetime,
) -> OutboxEvent:
    event_id = uuid4()
    alert_id = uuid4()

    event_data: dict[str, Any] = {
        "eventId": str(event_id),
        "eventType": AlertEventType.OPENED.value,
        "schemaVersion": 1,
        "occurredAt": "2026-08-26T10:00:00Z",
        "source": "opssight",
        "correlationId": str(alert_id),
        "payload": {
            "alertId": str(alert_id),
            "alertRuleId": str(uuid4()),
            "checkId": str(uuid4()),
            "status": "open",
            "severity": "critical",
            "message": "HTTP check failed",
            "openedAt": "2026-08-26T10:00:00Z",
            "recoveredAt": None,
        },
    }

    event = OutboxEvent(
        id=event_id,
        aggregate_type="alert",
        aggregate_id=alert_id,
        event_type=AlertEventType.OPENED.value,
        schema_version=1,
        occurred_at=now,
        source="opssight",
        correlation_id=str(alert_id),
        topic=settings.kafka_alert_topic,
        message_key=str(alert_id),
        event_data=event_data,
        next_attempt_at=now,
    )

    session.add(event)
    session.flush()

    return event


@database_test
def test_opening_alert_creates_outbox_event(
    session: Session,
) -> None:
    check, rule = create_alert_rule(
        session
    )

    changed_alerts = process_check_result(
        session,
        check.id,
        make_result(
            False,
            "HTTP check failed",
        ),
    )

    assert len(changed_alerts) == 1

    alert = changed_alerts[0]

    outbox_event = session.scalar(
        select(OutboxEvent).where(
            OutboxEvent.aggregate_id == alert.id,
            OutboxEvent.event_type
            == AlertEventType.OPENED.value,
        )
    )

    assert outbox_event is not None

    assert outbox_event.id is not None
    assert outbox_event.aggregate_type == "alert"
    assert outbox_event.aggregate_id == alert.id
    assert outbox_event.schema_version == 1
    assert outbox_event.source == "opssight"

    assert (
        outbox_event.topic
        == settings.kafka_alert_topic
    )

    assert (
        outbox_event.message_key
        == str(alert.id)
    )

    assert (
        outbox_event.correlation_id
        == str(alert.id)
    )

    assert outbox_event.published_at is None
    assert outbox_event.attempt_count == 0

    event = outbox_event.event_data

    assert event["eventId"] == str(
        outbox_event.id
    )

    assert (
        event["eventType"]
        == "nexusops.alert.opened"
    )

    assert event["schemaVersion"] == 1
    assert event["source"] == "opssight"

    assert event["correlationId"] == str(
        alert.id
    )

    assert event["occurredAt"].endswith(
        "Z"
    )

    payload = event["payload"]

    assert payload["alertId"] == str(
        alert.id
    )

    assert payload["alertRuleId"] == str(
        rule.id
    )

    assert payload["checkId"] == str(
        check.id
    )

    assert payload["status"] == "open"

    assert (
        payload["severity"]
        == AlertSeverity.CRITICAL.value
    )

    assert (
        payload["message"]
        == "HTTP check failed"
    )

    assert payload["openedAt"].endswith(
        "Z"
    )

    assert payload["recoveredAt"] is None


@database_test
def test_recovering_alert_creates_recovered_event(
    session: Session,
) -> None:
    check, _rule = create_alert_rule(
        session
    )

    opened = process_check_result(
        session,
        check.id,
        make_result(
            False,
            "HTTP check failed",
        ),
    )

    alert = opened[0]

    recovered = process_check_result(
        session,
        check.id,
        make_result(
            True,
            "HTTP check recovered",
        ),
    )

    assert recovered == [
        alert
    ]

    events = list(
        session.scalars(
            select(OutboxEvent)
            .where(
                OutboxEvent.aggregate_id
                == alert.id
            )
            .order_by(
                OutboxEvent.created_at
            )
        ).all()
    )

    assert len(events) == 2

    event_by_type = {
        event.event_type: event
        for event in events
    }

    opened_event = event_by_type[
        AlertEventType.OPENED.value
    ]

    recovered_event = event_by_type[
        AlertEventType.RECOVERED.value
    ]

    assert (
        opened_event.correlation_id
        == recovered_event.correlation_id
        == str(alert.id)
    )

    assert (
        recovered_event.event_data["eventType"]
        == "nexusops.alert.recovered"
    )

    payload = recovered_event.event_data[
        "payload"
    ]

    assert payload["status"] == "recovered"

    assert payload["recoveredAt"] is not None

    assert payload["recoveredAt"].endswith(
        "Z"
    )


@database_test
def test_transaction_rollback_removes_outbox_event(
    session: Session,
) -> None:
    check, _rule = create_alert_rule(
        session
    )

    changed_alerts = process_check_result(
        session,
        check.id,
        make_result(
            False,
            "transaction rollback test",
        ),
    )

    alert = changed_alerts[0]

    outbox_event = session.scalar(
        select(OutboxEvent).where(
            OutboxEvent.aggregate_id == alert.id
        )
    )

    assert outbox_event is not None

    alert_id = alert.id
    event_id = outbox_event.id

    session.rollback()

    assert (
        session.get(
            Alert,
            alert_id,
        )
        is None
    )

    assert (
        session.get(
            OutboxEvent,
            event_id,
        )
        is None
    )


@database_test
def test_successful_publication_marks_event_published(
    session: Session,
) -> None:
    now = datetime.now(UTC)

    event = create_pending_event(
        session,
        now,
    )

    publisher = RecordingPublisher()

    result = publish_pending_outbox_events(
        session,
        publisher_factory=lambda: publisher,
        now=now,
    )

    assert result.attempted == 1
    assert result.published == 1
    assert result.failed == 0

    assert event.attempt_count == 1
    assert event.last_attempt_at == now
    assert event.published_at == now
    assert event.last_error is None

    assert len(
        publisher.messages
    ) == 1

    topic, key, published_event = (
        publisher.messages[0]
    )

    assert topic == event.topic
    assert key == event.message_key

    assert (
        published_event
        == event.event_data
    )


@database_test
def test_failed_publication_remains_retryable(
    session: Session,
) -> None:
    now = datetime.now(UTC)

    event = create_pending_event(
        session,
        now,
    )

    publisher = RecordingPublisher(
        failures_remaining=1
    )

    first_result = publish_pending_outbox_events(
        session,
        publisher_factory=lambda: publisher,
        now=now,
    )

    assert first_result.attempted == 1
    assert first_result.published == 0
    assert first_result.failed == 1

    assert event.attempt_count == 1
    assert event.published_at is None
    assert event.last_error == "Kafka unavailable"

    assert event.next_attempt_at > now

    retry_time = event.next_attempt_at

    second_result = publish_pending_outbox_events(
        session,
        publisher_factory=lambda: publisher,
        now=retry_time,
    )

    assert second_result.attempted == 1
    assert second_result.published == 1
    assert second_result.failed == 0

    assert event.attempt_count == 2
    assert event.published_at == retry_time
    assert event.last_error is None


def test_kafka_publisher_serializes_event() -> None:
    producer = FakeConfluentProducer()

    publisher = KafkaEventPublisher(
        bootstrap_servers="127.0.0.1:9092",
        delivery_timeout_seconds=4.0,
        producer=producer,
    )

    event = {
        "eventId": str(uuid4()),
        "eventType": "nexusops.alert.opened",
    }

    publisher.publish(
        topic="nexusops.test.v1",
        key="alert-123",
        event=event,
    )

    assert producer.topic == "nexusops.test.v1"
    assert producer.key == b"alert-123"

    assert producer.value is not None

    assert json.loads(
        producer.value.decode("utf-8")
    ) == event

    assert producer.flush_timeout == 4.0


def test_kafka_publisher_rejects_delivery_failure() -> None:
    producer = FakeConfluentProducer(
        delivery_error=RuntimeError(
            "broker failure"
        )
    )

    publisher = KafkaEventPublisher(
        bootstrap_servers="127.0.0.1:9092",
        delivery_timeout_seconds=4.0,
        producer=producer,
    )

    with pytest.raises(
        RuntimeError,
        match="broker failure",
    ):
        publisher.publish(
            topic="nexusops.test.v1",
            key="alert-123",
            event={
                "eventId": str(uuid4())
            },
        )


def test_kafka_publisher_rejects_flush_timeout() -> None:
    producer = FakeConfluentProducer(
        remaining_messages=1
    )

    publisher = KafkaEventPublisher(
        bootstrap_servers="127.0.0.1:9092",
        delivery_timeout_seconds=4.0,
        producer=producer,
    )

    with pytest.raises(
        RuntimeError,
        match="1 message",
    ):
        publisher.publish(
            topic="nexusops.test.v1",
            key="alert-123",
            event={
                "eventId": str(uuid4())
            },
        )