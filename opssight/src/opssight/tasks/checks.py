import asyncio
import logging
from dataclasses import replace
from uuid import UUID

from opssight.alerting.engine import process_check_result
from opssight.celery_app import celery_app
from opssight.checks.executor import execute_check
from opssight.correlation import resolve_check_correlation_id
from opssight.database import SessionFactory
from opssight.metrics import record_check_execution
from opssight.models.check import Check

logger = logging.getLogger(__name__)


@celery_app.task(
    name="opssight.checks.execute",
)
def execute_check_task(
    check_id: str,
    correlation_id: str | None = None,
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

        resolved_correlation_id = (
            resolve_check_correlation_id(
                check.id,
                result.started_at,
                correlation_id,
            )
        )

        result = replace(
            result,
            correlation_id=resolved_correlation_id,
        )

        record_check_execution(
            check_type=check.check_type,
            success=result.success,
            duration_ms=result.duration_ms,
        )

        process_check_result(
            session,
            check.id,
            result,
        )

        session.commit()

        logger.info(
            "check_processed",
            extra={
                "check_id": str(check.id),
                "correlation_id": resolved_correlation_id,
            },
        )
