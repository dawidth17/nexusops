import logging

from opssight.celery_app import celery_app
from opssight.database import SessionFactory
from opssight.outbox import (
    publish_pending_outbox_events,
)

logger = logging.getLogger(__name__)


@celery_app.task(
    name="opssight.outbox.publish",
)
def publish_outbox_events_task() -> None:
    with SessionFactory() as session:
        result = publish_pending_outbox_events(
            session
        )

        session.commit()

    if result.attempted > 0:
        logger.info(
            "outbox_publish_cycle "
            "attempted=%s "
            "published=%s "
            "failed=%s",
            result.attempted,
            result.published,
            result.failed,
        )