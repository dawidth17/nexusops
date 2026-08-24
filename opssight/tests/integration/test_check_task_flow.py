from datetime import UTC, datetime
from uuid import uuid4

from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.checks.result import CheckResult
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import (
    AlertSeverity,
    AlertStatus,
)
from opssight.models.host import Host
from opssight.tasks import checks as check_tasks


def test_check_task_updates_alert_state(
    integration_session: Session,
    monkeypatch,
) -> None:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"integration-task-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name=f"integration-task-check-{suffix}",
        check_type="http",
        target="https://example.com",
        interval_seconds=60,
        timeout_seconds=5,
    )

    rule = AlertRule(
        check=check,
        name=f"integration-task-rule-{suffix}",
        severity=AlertSeverity.CRITICAL.value,
        failure_threshold=1,
    )

    integration_session.add(host)
    integration_session.flush()

    check_id = check.id
    rule_id = rule.id

    integration_session.commit()

    results = [
        CheckResult(
            success=False,
            started_at=datetime(
                2026,
                8,
                24,
                12,
                0,
                tzinfo=UTC,
            ),
            duration_ms=100.0,
            message="simulated http failure",
        ),
        CheckResult(
            success=True,
            started_at=datetime(
                2026,
                8,
                24,
                12,
                1,
                tzinfo=UTC,
            ),
            duration_ms=80.0,
            message="simulated http recovery",
        ),
    ]

    async def fake_execute_check(
        _check: Check,
    ) -> CheckResult:
        return results.pop(0)

    monkeypatch.setattr(
        check_tasks,
        "execute_check",
        fake_execute_check,
    )

    check_tasks.execute_check_task.run(
        str(check_id)
    )

    integration_session.expire_all()

    stored_check = integration_session.get(
        Check,
        check_id,
    )

    open_alert = integration_session.scalar(
        select(Alert).where(
            Alert.alert_rule_id == rule_id
        )
    )

    assert stored_check is not None
    assert stored_check.consecutive_failures == 1

    assert open_alert is not None
    assert open_alert.status == AlertStatus.OPEN.value
    assert open_alert.message == "simulated http failure"
    assert open_alert.recovered_at is None

    alert_id = open_alert.id

    check_tasks.execute_check_task.run(
        str(check_id)
    )

    integration_session.expire_all()

    stored_check = integration_session.get(
        Check,
        check_id,
    )

    recovered_alert = integration_session.get(
        Alert,
        alert_id,
    )

    assert stored_check is not None
    assert stored_check.consecutive_failures == 0

    assert recovered_alert is not None
    assert recovered_alert.status == AlertStatus.RECOVERED.value
    assert recovered_alert.recovered_at is not None

    alerts = list(
        integration_session.scalars(
            select(Alert).where(
                Alert.alert_rule_id == rule_id
            )
        ).all()
    )

    assert len(alerts) == 1

    integration_session.delete(
        host
    )
    integration_session.commit()
