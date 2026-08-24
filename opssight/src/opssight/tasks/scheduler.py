from datetime import UTC, datetime, timedelta
from uuid import UUID

from sqlalchemy import select

from opssight.celery_app import celery_app
from opssight.database import SessionFactory
from opssight.models.check import Check
from opssight.tasks.checks import execute_check_task
from opssight.metrics import record_scheduler_dispatch


@celery_app.task(
    name="opssight.scheduler.dispatch_due_checks",
)
def dispatch_due_checks() -> int:
    now = datetime.now(UTC)

    with SessionFactory() as session:
        due_checks = list(
            session.scalars(
                select(Check)
                .where(
                    Check.enabled.is_(True),
                    Check.next_run_at <= now,
                )
                .order_by(Check.next_run_at)
                .with_for_update(
                    skip_locked=True,
                )
            ).all()
        )

        scheduled_checks: list[UUID] = []

        for check in due_checks:
            check.next_run_at = now + timedelta(
                seconds=check.interval_seconds
            )

            scheduled_checks.append(check.id)

        session.commit()

    for check_id in scheduled_checks:
        execute_check_task.delay(
            str(check_id)
        )

        record_scheduler_dispatch()

    return len(scheduled_checks)