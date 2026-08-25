from collections.abc import Generator
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import inspect, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from opssight.config import settings
from opssight.database import SessionFactory, engine
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import AlertSeverity, AlertStatus
from opssight.models.host import Host
from opssight.repositories.host_repository import create_host, get_host_by_id
from opssight.repositories.telemetry_repository import (
    create_telemetry,
    list_telemetry_by_time_range,
)

pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="database tests require a dedicated test database",
)



@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


def test_database_schema() -> None:
    inspector = inspect(engine)

    tables = set(inspector.get_table_names())

    assert {
        "alembic_version",
        "hosts",
        "checks",
        "alert_rules",
        "alerts",
        "telemetry",
    }.issubset(tables)


def test_telemetry_is_hypertable(session: Session) -> None:
    hypertable_name = session.execute(
        text(
            """
            SELECT hypertable_name
            FROM timescaledb_information.hypertables
            WHERE hypertable_schema = 'public'
              AND hypertable_name = 'telemetry'
            """
        )
    ).scalar_one()

    assert hypertable_name == "telemetry"


def test_telemetry_chunk_interval(session: Session) -> None:
    time_interval = session.execute(
        text(
            """
            SELECT time_interval
            FROM timescaledb_information.dimensions
            WHERE hypertable_schema = 'public'
              AND hypertable_name = 'telemetry'
              AND column_name = 'captured_at'
            """
        )
    ).scalar_one()

    assert time_interval == timedelta(days=1)


def test_telemetry_retention_policy(session: Session) -> None:
    policy_count = session.execute(
        text(
            """
            SELECT COUNT(*)
            FROM timescaledb_information.jobs
            WHERE hypertable_schema = 'public'
              AND hypertable_name = 'telemetry'
              AND proc_name = 'policy_retention'
            """
        )
    ).scalar_one()

    assert policy_count == 1


def test_host_can_be_persisted(session: Session) -> None:
    host = create_host(
        session,
        name="database-test-host",
        address="127.0.0.1",
    )

    session.commit()

    stored_host = get_host_by_id(session, host.id)

    assert stored_host is not None
    assert stored_host.name == "database-test-host"
    assert stored_host.address == "127.0.0.1"
    assert stored_host.enabled is True


def test_monitoring_relationships(session: Session) -> None:
    host = Host(
        name="relationship-test-host",
        address="10.0.0.10",
    )

    check = Check(
        host=host,
        name="http-health",
        check_type="http",
        target="http://10.0.0.10/health",
        interval_seconds=60,
        timeout_seconds=5,
    )

    alert_rule = AlertRule(
        check=check,
        name="service-unavailable",
        severity=AlertSeverity.CRITICAL.value,
        failure_threshold=3,
    )

    alert = Alert(
        alert_rule=alert_rule,
        status=AlertStatus.OPEN.value,
        message="service health check failed",
    )

    session.add(host)
    session.commit()

    assert check.host is host
    assert check in host.checks

    assert alert_rule.check is check
    assert alert_rule in check.alert_rules

    assert alert.alert_rule is alert_rule
    assert alert in alert_rule.alerts


def test_telemetry_can_be_persisted_and_queried(
    session: Session,
) -> None:
    host = Host(
        name="telemetry-test-host",
        address="10.0.0.30",
    )

    session.add(host)
    session.flush()

    now = datetime.now(UTC)

    create_telemetry(
        session,
        host_id=host.id,
        captured_at=now - timedelta(minutes=2),
        metric_name="cpu.utilization",
        value=20.5,
        unit="percent",
        labels={"core": "all"},
    )

    create_telemetry(
        session,
        host_id=host.id,
        captured_at=now - timedelta(minutes=1),
        metric_name="cpu.utilization",
        value=35.2,
        unit="percent",
        labels={"core": "all"},
    )

    session.commit()

    telemetry = list_telemetry_by_time_range(
        session,
        host_id=host.id,
        metric_name="cpu.utilization",
        start_time=now - timedelta(minutes=5),
        end_time=now + timedelta(minutes=1),
    )

    assert len(telemetry) == 2

    assert telemetry[0].value == 20.5
    assert telemetry[1].value == 35.2

    assert telemetry[0].unit == "percent"
    assert telemetry[0].labels == {"core": "all"}


def test_invalid_check_interval_is_rejected(
    session: Session,
) -> None:
    host = Host(
        name="constraint-test-host",
        address="10.0.0.20",
    )

    _invalid_check = Check(
        host=host,
        name="invalid-check",
        check_type="tcp",
        target="10.0.0.20:443",
        interval_seconds=0,
        timeout_seconds=5,
    )

    session.add(host)

    with pytest.raises(IntegrityError):
        session.commit()

    session.rollback()