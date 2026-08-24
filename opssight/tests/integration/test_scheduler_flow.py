from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from sqlalchemy.orm import Session

from opssight.models.check import Check
from opssight.models.host import Host
from opssight.tasks.checks import execute_check_task
from opssight.tasks.scheduler import dispatch_due_checks


def test_scheduler_dispatches_only_due_enabled_checks(
    integration_session: Session,
    monkeypatch,
) -> None:
    suffix = uuid4().hex[:8]
    now = datetime.now(UTC)

    host = Host(
        name=f"integration-scheduler-host-{suffix}",
        address="127.0.0.1",
    )

    due_check = Check(
        host=host,
        name=f"due-check-{suffix}",
        check_type="dns",
        target="localhost",
        interval_seconds=60,
        timeout_seconds=5,
        next_run_at=now - timedelta(minutes=1),
        enabled=True,
    )

    future_check = Check(
        host=host,
        name=f"future-check-{suffix}",
        check_type="dns",
        target="localhost",
        interval_seconds=60,
        timeout_seconds=5,
        next_run_at=now + timedelta(hours=1),
        enabled=True,
    )

    disabled_check = Check(
        host=host,
        name=f"disabled-check-{suffix}",
        check_type="dns",
        target="localhost",
        interval_seconds=60,
        timeout_seconds=5,
        next_run_at=now - timedelta(minutes=1),
        enabled=False,
    )

    integration_session.add(host)
    integration_session.flush()

    due_check_id = due_check.id
    future_check_id = future_check.id
    disabled_check_id = disabled_check.id

    future_next_run_at = future_check.next_run_at
    disabled_next_run_at = disabled_check.next_run_at

    integration_session.commit()

    dispatched_check_ids: list[UUID] = []

    def fake_delay(check_id: str) -> None:
        dispatched_check_ids.append(
            UUID(check_id)
        )

    monkeypatch.setattr(
        execute_check_task,
        "delay",
        fake_delay,
    )

    before_dispatch = datetime.now(UTC)

    dispatched_count = dispatch_due_checks.run()

    assert dispatched_count == 1
    assert dispatched_check_ids == [
        due_check_id
    ]

    integration_session.expire_all()

    updated_due_check = integration_session.get(
        Check,
        due_check_id,
    )

    updated_future_check = integration_session.get(
        Check,
        future_check_id,
    )

    updated_disabled_check = integration_session.get(
        Check,
        disabled_check_id,
    )

    assert updated_due_check is not None
    assert updated_future_check is not None
    assert updated_disabled_check is not None

    assert (
        updated_due_check.next_run_at
        > before_dispatch
    )

    assert (
        updated_future_check.next_run_at
        == future_next_run_at
    )

    assert (
        updated_disabled_check.next_run_at
        == disabled_next_run_at
    )

    integration_session.delete(
        host
    )
    integration_session.commit()