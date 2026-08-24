from celery import Celery

from opssight.messaging import build_broker_url


celery_app = Celery(
    "opssight",
    broker=build_broker_url(),
    include=[
        "opssight.tasks.system",
        "opssight.tasks.checks",
        "opssight.tasks.scheduler",
    ],
)

celery_app.conf.update(
    accept_content=["json"],
    task_serializer="json",
    task_ignore_result=True,
    timezone="UTC",
    enable_utc=True,
    broker_connection_retry_on_startup=True,
    beat_schedule={
        "dispatch-due-checks": {
            "task": "opssight.scheduler.dispatch_due_checks",
            "schedule": 5.0,
        },
    },
)