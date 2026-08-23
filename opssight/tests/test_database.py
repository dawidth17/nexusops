from collections.abc import Generator

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import inspect
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


pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="database tests require a dedicated test database",
)


@pytest.fixture(scope="module", autouse=True)
def migrate_database() -> Generator[None, None, None]:
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


def test_database_schema() -> None:
    inspector = inspect(engine)

    tables = set(inspector.get_table_names())

    assert {
        "alembic_version",
        "hosts",
        "checks",
        "alert_rules",
        "alerts",
    }.issubset(tables)


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


def test_invalid_check_interval_is_rejected(
    session: Session,
) -> None:
    host = Host(
        name="constraint-test-host",
        address="10.0.0.20",
    )

    invalid_check = Check(
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