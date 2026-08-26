import logging
from datetime import timedelta
from enum import StrEnum
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.checks.result import CheckResult
from opssight.correlation import resolve_check_correlation_id
from opssight.metrics import record_alert_transition
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import AlertStatus
from opssight.outbox import (
    AlertEventType,
    enqueue_alert_event,
)

logger = logging.getLogger(__name__)


class AlertAction(StrEnum):
    NONE = "none"
    OPEN = "open"
    RECOVER = "recover"


def calculate_consecutive_failures(
    current_failures: int,
    success: bool,
) -> int:
    if success:
        return 0

    return current_failures + 1


def decide_alert_action(
    success: bool,
    consecutive_failures: int,
    failure_threshold: int,
    has_open_alert: bool,
) -> AlertAction:
    if success:
        if has_open_alert:
            return AlertAction.RECOVER

        return AlertAction.NONE

    if (
        consecutive_failures >= failure_threshold
        and not has_open_alert
    ):
        return AlertAction.OPEN

    return AlertAction.NONE


def process_check_result(
    session: Session,
    check_id: UUID,
    result: CheckResult,
) -> list[Alert]:
    check = session.scalar(
        select(Check)
        .where(Check.id == check_id)
        .with_for_update()
    )

    if check is None:
        raise ValueError(
            f"check {check_id} was not found"
        )

    consecutive_failures = calculate_consecutive_failures(
        check.consecutive_failures,
        result.success,
    )

    check.consecutive_failures = consecutive_failures

    rules = list(
        session.scalars(
            select(AlertRule)
            .where(AlertRule.check_id == check.id)
            .order_by(AlertRule.name)
        ).all()
    )

    result_time = result.started_at + timedelta(
        milliseconds=result.duration_ms
    )

    changed_alerts: list[Alert] = []

    for rule in rules:
        open_alert = session.scalar(
            select(Alert).where(
                Alert.alert_rule_id == rule.id,
                Alert.status == AlertStatus.OPEN.value,
            )
        )

        if not rule.enabled and not result.success:
            continue

        action = decide_alert_action(
            success=result.success,
            consecutive_failures=consecutive_failures,
            failure_threshold=rule.failure_threshold,
            has_open_alert=open_alert is not None,
        )

        if action == AlertAction.OPEN:
            correlation_id = resolve_check_correlation_id(
                check.id,
                result.started_at,
                result.correlation_id,
            )

            alert = Alert(
                alert_rule_id=rule.id,
                status=AlertStatus.OPEN.value,
                correlation_id=correlation_id,
                message=result.message,
                opened_at=result_time,
            )

            session.add(alert)

            outbox_event = enqueue_alert_event(
                session=session,
                alert=alert,
                rule=rule,
                event_type=AlertEventType.OPENED,
            )

            changed_alerts.append(alert)

            record_alert_transition(
                action=AlertAction.OPEN.value,
                severity=rule.severity,
            )

            logger.info(
                "alert_opened",
                extra={
                    "alert_id": str(alert.id),
                    "event_id": str(outbox_event.id),
                    "check_id": str(check.id),
                    "correlation_id": correlation_id,
                },
            )

        elif (
            action == AlertAction.RECOVER
            and open_alert is not None
        ):
            open_alert.status = AlertStatus.RECOVERED.value
            open_alert.recovered_at = result_time

            outbox_event = enqueue_alert_event(
                session=session,
                alert=open_alert,
                rule=rule,
                event_type=AlertEventType.RECOVERED,
            )

            changed_alerts.append(open_alert)

            record_alert_transition(
                action=AlertAction.RECOVER.value,
                severity=rule.severity,
            )

            logger.info(
                "alert_recovered",
                extra={
                    "alert_id": str(open_alert.id),
                    "event_id": str(outbox_event.id),
                    "check_id": str(check.id),
                    "correlation_id": open_alert.correlation_id,
                },
            )

    session.flush()

    return changed_alerts
