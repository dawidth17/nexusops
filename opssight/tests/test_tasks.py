import asyncio
from collections.abc import Generator
from datetime import UTC, datetime
from unittest.mock import AsyncMock
from uuid import uuid4

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import select
from sqlalchemy.orm import Session

import opssight.checks.executor as executor
import opssight.tasks.checks as check_tasks
from opssight.checks.executor import execute_check, parse_host_port
from opssight.checks.result import CheckResult
from opssight.config import settings
from opssight.database import SessionFactory, engine
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import AlertSeverity, AlertStatus
from opssight.models.host import Host
from opssight.tasks.checks import execute_check_task


database_test = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="database tests require a dedicated test database",
)



@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


def test_parse_host_port() -> None:
    host, port = parse_host_port(
        "127.0.0.1:5432"
    )

    assert host == "127.0.0.1"
    assert port == 5432


def test_parse_host_port_uses_default_port() -> None:
    host, port = parse_host_port(
        "example.com",
        default_port=443,
    )

    assert host == "example.com"
    assert port == 443


def test_parse_host_port_requires_port() -> None:
    with pytest.raises(
        ValueError,
        match="port is required",
    ):
        parse_host_port("example.com")


def test_dns_check_is_dispatched(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    expected_result = CheckResult(
        success=True,
        started_at=datetime.now(UTC),
        duration_ms=5.0,
        message="dns succeeded",
    )

    dns_check = AsyncMock(
        return_value=expected_result
    )

    monkeypatch.setattr(
        executor,
        "run_dns_check",
        dns_check,
    )

    check = Check(
        name="dns-check",
        check_type="dns",
        target="example.com",
        timeout_seconds=4,
    )

    result = asyncio.run(
        execute_check(check)
    )

    assert result is expected_result

    dns_check.assert_awaited_once_with(
        "example.com",
        timeout_seconds=4.0,
    )


def test_tcp_check_is_dispatched(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    expected_result = CheckResult(
        success=True,
        started_at=datetime.now(UTC),
        duration_ms=5.0,
        message="tcp succeeded",
    )

    tcp_check = AsyncMock(
        return_value=expected_result
    )

    monkeypatch.setattr(
        executor,
        "run_tcp_check",
        tcp_check,
    )

    check = Check(
        name="tcp-check",
        check_type="tcp",
        target="127.0.0.1:5432",
        timeout_seconds=3,
    )

    result = asyncio.run(
        execute_check(check)
    )

    assert result is expected_result

    tcp_check.assert_awaited_once_with(
        "127.0.0.1",
        5432,
        timeout_seconds=3.0,
    )


def test_http_check_is_dispatched(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    expected_result = CheckResult(
        success=True,
        started_at=datetime.now(UTC),
        duration_ms=5.0,
        message="http succeeded",
    )

    http_check = AsyncMock(
        return_value=expected_result
    )

    monkeypatch.setattr(
        executor,
        "run_http_check",
        http_check,
    )

    check = Check(
        name="http-check",
        check_type="http",
        target="http://service.test/health",
        timeout_seconds=5,
    )

    result = asyncio.run(
        execute_check(check)
    )

    assert result is expected_result

    http_check.assert_awaited_once_with(
        "http://service.test/health",
        timeout_seconds=5.0,
    )


def test_tls_check_is_dispatched(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    expected_result = CheckResult(
        success=True,
        started_at=datetime.now(UTC),
        duration_ms=5.0,
        message="tls succeeded",
    )

    tls_check = AsyncMock(
        return_value=expected_result
    )

    monkeypatch.setattr(
        executor,
        "run_tls_check",
        tls_check,
    )

    check = Check(
        name="tls-check",
        check_type="tls",
        target="example.com",
        timeout_seconds=6,
    )

    result = asyncio.run(
        execute_check(check)
    )

    assert result is expected_result

    tls_check.assert_awaited_once_with(
        "example.com",
        443,
        timeout_seconds=6.0,
    )


def test_unsupported_check_type_is_rejected() -> None:
    check = Check(
        name="unsupported-check",
        check_type="something-else",
        target="example.com",
        timeout_seconds=5,
    )

    with pytest.raises(
        ValueError,
        match="unsupported check type",
    ):
        asyncio.run(
            execute_check(check)
        )


def test_invalid_task_check_id_is_rejected() -> None:
    with pytest.raises(
        ValueError,
        match="invalid check id",
    ):
        execute_check_task.run(
            "not-a-valid-uuid"
        )


@database_test
def test_check_task_processes_result(
    session: Session,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"task-test-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="dns-health",
        check_type="dns",
        target="service.test",
        timeout_seconds=5,
    )

    rule = AlertRule(
        check=check,
        name="dns-unavailable",
        severity=AlertSeverity.WARNING.value,
        failure_threshold=1,
    )

    session.add(host)
    session.commit()

    result = CheckResult(
        success=False,
        started_at=datetime.now(UTC),
        duration_ms=10.0,
        message="dns resolution failed",
    )

    mocked_executor = AsyncMock(
        return_value=result
    )

    monkeypatch.setattr(
        check_tasks,
        "execute_check",
        mocked_executor,
    )

    execute_check_task.run(
        str(check.id)
    )

    session.expire_all()

    stored_check = session.get(
        Check,
        check.id,
    )

    assert stored_check is not None
    assert stored_check.consecutive_failures == 1

    alert = session.scalar(
        select(Alert).where(
            Alert.alert_rule_id == rule.id
        )
    )

    assert alert is not None
    assert alert.status == AlertStatus.OPEN.value
    assert alert.message == "dns resolution failed"

    assert mocked_executor.await_count == 1


@database_test
def test_disabled_check_is_not_executed(
    session: Session,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"disabled-task-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="disabled-check",
        check_type="dns",
        target="service.test",
        timeout_seconds=5,
        enabled=False,
    )

    session.add(host)
    session.commit()

    mocked_executor = AsyncMock()

    monkeypatch.setattr(
        check_tasks,
        "execute_check",
        mocked_executor,
    )

    execute_check_task.run(
        str(check.id)
    )

    mocked_executor.assert_not_awaited()


@database_test
def test_missing_task_check_is_rejected(
    session: Session,
) -> None:
    missing_check_id = uuid4()

    with pytest.raises(
        ValueError,
        match=f"check {missing_check_id} was not found",
    ):
        execute_check_task.run(
            str(missing_check_id)
        )