from opssight.celery_app import celery_app


@celery_app.task(
    name="opssight.system.ping",
)
def ping() -> str:
    return "pong"