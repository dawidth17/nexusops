from urllib.parse import quote

from opssight.config import settings


def build_broker_url() -> str:
    username = quote(
        settings.rabbitmq_user,
        safe="",
    )

    password = quote(
        settings.rabbitmq_password.get_secret_value(),
        safe="",
    )

    return (
        f"amqp://{username}:{password}"
        f"@{settings.rabbitmq_host}:{settings.rabbitmq_port}//"
    )