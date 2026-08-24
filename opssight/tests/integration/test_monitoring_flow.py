from datetime import UTC, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.alerting.engine import process_check_result
from opssight.checks.result import CheckResult
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import (
    AlertSeverity,
    AlertStatus,
)
from opssight.models.host import Host


def test_monitoring_failure_and_recovery_flow(
    integration_session: Session,
) -> None:
    host = Host(
        name="integration-web-server",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="integration-http-check",
        check_type="http",
        target="https://example.com",
        interval_seconds=60,
        timeout_seconds=5,
    )

    rule = AlertRule(
        check=check,
        name="integration-http-failure-rule",
        severity=AlertSeverity.CRITICAL.value,
        failure_threshold=2,
    )

    integration_session.add(host)
    integration_session.flush()

    start = datetime(
        2026,
        8,
        24,
        12,
        0,
        tzinfo=UTC,
    )

    first_failure = CheckResult(
        success=False,
        started_at=start,
        duration_ms=100.0,
        message="http check failed",
    )

    changed_alerts = process_check_result(
        integration_session,
        check.id,
        first_failure,
    )

    assert changed_alerts == []
    assert check.consecutive_failures == 1

    alerts = list(
        integration_session.scalars(
            select(Alert).where(
                Alert.alert_rule_id == rule.id
            )
        ).all()
    )

    assert alerts == []

    second_failure = CheckResult(
        success=False,
        started_at=start + timedelta(minutes=1),
        duration_ms=120.0,
        message="http check failed again",
    )

    changed_alerts = process_check_result(
        integration_session,
        check.id,
        second_failure,
    )

    assert len(changed_alerts) == 1
    assert check.consecutive_failures == 2

    open_alert = changed_alerts[0]

    assert open_alert.status == AlertStatus.OPEN.value
    assert open_alert.message == "http check failed again"
    assert open_alert.recovered_at is None

    alerts = list(
        integration_session.scalars(
            select(Alert).where(
                Alert.alert_rule_id == rule.id
            )
        ).all()
    )

    assert len(alerts) == 1
    assert alerts[0].id == open_alert.id

    success = CheckResult(
        success=True,
        started_at=start + timedelta(minutes=2),
        duration_ms=80.0,
        message="http check succeeded",
    )

    changed_alerts = process_check_result(
        integration_session,
        check.id,
        success,
    )

    assert len(changed_alerts) == 1
    assert check.consecutive_failures == 0

    recovered_alert = changed_alerts[0]

    assert recovered_alert.id == open_alert.id
    assert (
        recovered_alert.status
        == AlertStatus.RECOVERED.value
    )
    assert recovered_alert.recovered_at is not None

    alerts = list(
        integration_session.scalars(
            select(Alert).where(
                Alert.alert_rule_id == rule.id
            )
        ).all()
    )

    assert len(alerts) == 1
    assert (
        alerts[0].status
        == AlertStatus.RECOVERED.value
    )