from collections.abc import Generator
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.alerting.engine import (
    AlertAction,
    calculate_consecutive_failures,
    decide_alert_action,
    process_check_result,
)
from opssight.checks.result import CheckResult
from opssight.config import settings
from opssight.database import SessionFactory, engine
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import AlertSeverity, AlertStatus
from opssight.models.host import Host


database_test = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="database tests require a dedicated test database",
)


@pytest.fixture(scope="module", autouse=True)
def migrate_database() -> Generator[None, None, None]:
    if not settings.db_name.endswith("_test"):
        yield
        return

    alembic_config = Config("alembic.ini")

    command.downgrade(alembic_config, "base")
    command.upgrade(alembic_config, "head")

    yield

    engine.dispose()


@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


def make_result(
    success: bool,
    message: str = "check result",
) -> CheckResult:
    return CheckResult(
        success=success,
        started_at=datetime.now(UTC),
        duration_ms=10.0,
        message=message,
    )


def test_failure_increments_counter() -> None:
    failures = calculate_consecutive_failures(
        current_failures=2,
        success=False,
    )

    assert failures == 3


def test_success_resets_counter() -> None:
    failures = calculate_consecutive_failures(
        current_failures=7,
        success=True,
    )

    assert failures == 0


def test_failure_below_threshold_does_nothing() -> None:
    action = decide_alert_action(
        success=False,
        consecutive_failures=2,
        failure_threshold=3,
        has_open_alert=False,
    )

    assert action == AlertAction.NONE


def test_failure_at_threshold_opens_alert() -> None:
    action = decide_alert_action(
        success=False,
        consecutive_failures=3,
        failure_threshold=3,
        has_open_alert=False,
    )

    assert action == AlertAction.OPEN


def test_existing_open_alert_is_not_duplicated() -> None:
    action = decide_alert_action(
        success=False,
        consecutive_failures=4,
        failure_threshold=3,
        has_open_alert=True,
    )

    assert action == AlertAction.NONE


def test_success_recovers_open_alert() -> None:
    action = decide_alert_action(
        success=True,
        consecutive_failures=0,
        failure_threshold=3,
        has_open_alert=True,
    )

    assert action == AlertAction.RECOVER


@database_test
def test_alert_lifecycle_and_deduplication(
    session: Session,
) -> None:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"alert-lifecycle-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="http-health",
        check_type="http",
        target="http://127.0.0.1/health",
    )

    rule = AlertRule(
        check=check,
        name="three-consecutive-failures",
        severity=AlertSeverity.CRITICAL.value,
        failure_threshold=3,
    )

    session.add(host)
    session.flush()

    first_failure = process_check_result(
        session,
        check.id,
        make_result(False, "first failure"),
    )

    assert check.consecutive_failures == 1
    assert first_failure == []

    second_failure = process_check_result(
        session,
        check.id,
        make_result(False, "second failure"),
    )

    assert check.consecutive_failures == 2
    assert second_failure == []

    third_failure = process_check_result(
        session,
        check.id,
        make_result(False, "third failure"),
    )

    assert check.consecutive_failures == 3
    assert len(third_failure) == 1
    assert third_failure[0].status == AlertStatus.OPEN.value
    assert third_failure[0].message == "third failure"

    fourth_failure = process_check_result(
        session,
        check.id,
        make_result(False, "fourth failure"),
    )

    assert check.consecutive_failures == 4
    assert fourth_failure == []

    alerts = list(
        session.scalars(
            select(Alert).where(
                Alert.alert_rule_id == rule.id
            )
        ).all()
    )

    assert len(alerts) == 1
    assert alerts[0].status == AlertStatus.OPEN.value

    recovery = process_check_result(
        session,
        check.id,
        make_result(True, "check recovered"),
    )

    assert check.consecutive_failures == 0
    assert len(recovery) == 1
    assert recovery[0].status == AlertStatus.RECOVERED.value
    assert recovery[0].recovered_at is not None


@database_test
def test_new_incident_creates_new_alert_after_recovery(
    session: Session,
) -> None:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"second-incident-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="tcp-health",
        check_type="tcp",
        target="127.0.0.1:443",
    )

    rule = AlertRule(
        check=check,
        name="two-consecutive-failures",
        severity=AlertSeverity.WARNING.value,
        failure_threshold=2,
    )

    session.add(host)
    session.flush()

    process_check_result(
        session,
        check.id,
        make_result(False),
    )

    process_check_result(
        session,
        check.id,
        make_result(False),
    )

    process_check_result(
        session,
        check.id,
        make_result(True),
    )

    process_check_result(
        session,
        check.id,
        make_result(False),
    )

    process_check_result(
        session,
        check.id,
        make_result(False),
    )

    alerts = list(
        session.scalars(
            select(Alert)
            .where(Alert.alert_rule_id == rule.id)
            .order_by(Alert.opened_at)
        ).all()
    )

    assert len(alerts) == 2

    assert alerts[0].status == AlertStatus.RECOVERED.value
    assert alerts[0].recovered_at is not None

    assert alerts[1].status == AlertStatus.OPEN.value
    assert alerts[1].recovered_at is None


@database_test
def test_disabled_rule_does_not_open_alert(
    session: Session,
) -> None:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"disabled-rule-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="dns-health",
        check_type="dns",
        target="service.test",
    )

    rule = AlertRule(
        check=check,
        name="disabled-rule",
        severity=AlertSeverity.WARNING.value,
        failure_threshold=1,
        enabled=False,
    )

    session.add(host)
    session.flush()

    changed_alerts = process_check_result(
        session,
        check.id,
        make_result(False),
    )

    alerts = list(
        session.scalars(
            select(Alert).where(
                Alert.alert_rule_id == rule.id
            )
        ).all()
    )

    assert changed_alerts == []
    assert alerts == []


@database_test
def test_missing_check_is_rejected(
    session: Session,
) -> None:
    missing_check_id = uuid4()

    with pytest.raises(
        ValueError,
        match=f"check {missing_check_id} was not found",
    ):
        process_check_result(
            session,
            missing_check_id,
            make_result(False),
        )