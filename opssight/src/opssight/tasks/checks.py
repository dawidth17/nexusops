import asyncio
from uuid import UUID

from opssight.alerting.engine import process_check_result
from opssight.celery_app import celery_app
from opssight.checks.executor import execute_check
from opssight.database import SessionFactory
from opssight.models.check import Check


@celery_app.task(
    name="opssight.checks.execute",
)
def execute_check_task(
    check_id: str,
) -> None:
    try:
        parsed_check_id = UUID(check_id)
    except ValueError as error:
        raise ValueError(
            f"invalid check id: {check_id}"
        ) from error

    with SessionFactory() as session:
        check = session.get(
            Check,
            parsed_check_id,
        )

        if check is None:
            raise ValueError(
                f"check {parsed_check_id} was not found"
            )

        if not check.enabled:
            return

        result = asyncio.run(
            execute_check(check)
        )

        process_check_result(
            session,
            check.id,
            result,
        )

        session.commit()