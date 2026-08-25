from collections.abc import Generator
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from unittest.mock import Mock
from uuid import uuid4

import pytest
from sqlalchemy.orm import Session

import opssight.tasks.scheduler as scheduler_tasks
from opssight.config import settings
from opssight.database import SessionFactory
from opssight.models.check import Check
from opssight.models.host import Host
from opssight.tasks.scheduler import dispatch_due_checks

pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="scheduler tests require a dedicated test database",
)



@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


def test_dispatches_only_due_enabled_checks(
    session: Session,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    now = datetime.now(UTC)
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"scheduler-test-host-{suffix}",
        address="127.0.0.1",
    )

    due_check = Check(
        host=host,
        name="due-check",
        check_type="dns",
        target="localhost",
        interval_seconds=60,
        next_run_at=now - timedelta(minutes=1),
        enabled=True,
    )

    future_check = Check(
        host=host,
        name="future-check",
        check_type="dns",
        target="localhost",
        interval_seconds=60,
        next_run_at=now + timedelta(hours=1),
        enabled=True,
    )

    disabled_check = Check(
        host=host,
        name="disabled-check",
        check_type="dns",
        target="localhost",
        interval_seconds=60,
        next_run_at=now - timedelta(minutes=1),
        enabled=False,
    )

    session.add(host)
    session.commit()

    delay = Mock()

    fake_execute_task = SimpleNamespace(
        delay=delay,
    )

    monkeypatch.setattr(
        scheduler_tasks,
        "execute_check_task",
        fake_execute_task,
    )

    scheduled_count = dispatch_due_checks.run()

    assert scheduled_count == 1

    delay.assert_called_once_with(
        str(due_check.id)
    )

    session.expire_all()

    stored_due_check = session.get(
        Check,
        due_check.id,
    )

    stored_future_check = session.get(
        Check,
        future_check.id,
    )

    stored_disabled_check = session.get(
        Check,
        disabled_check.id,
    )

    assert stored_due_check is not None
    assert stored_future_check is not None
    assert stored_disabled_check is not None

    assert stored_due_check.next_run_at > now

    assert (
        stored_future_check.next_run_at
        == future_check.next_run_at
    )

    assert (
        stored_disabled_check.next_run_at
        == disabled_check.next_run_at
    )


def test_check_is_not_dispatched_again_before_next_run(
    session: Session,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    now = datetime.now(UTC)
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"scheduler-repeat-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name="scheduled-check",
        check_type="dns",
        target="localhost",
        interval_seconds=300,
        next_run_at=now - timedelta(seconds=1),
    )

    session.add(host)
    session.commit()

    delay = Mock()

    fake_execute_task = SimpleNamespace(
        delay=delay,
    )

    monkeypatch.setattr(
        scheduler_tasks,
        "execute_check_task",
        fake_execute_task,
    )

    first_count = dispatch_due_checks.run()
    second_count = dispatch_due_checks.run()

    assert first_count == 1
    assert second_count == 0

    delay.assert_called_once_with(
        str(check.id)
    )