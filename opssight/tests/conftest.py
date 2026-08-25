from collections.abc import Generator

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import text

from opssight.config import settings
from opssight.database import engine


@pytest.fixture(scope="session", autouse=True)
def prepare_test_database() -> Generator[None, None, None]:
    if not settings.db_name.endswith("_test"):
        yield
        return

    alembic_config = Config("alembic.ini")

    engine.dispose()

    command.downgrade(
        alembic_config,
        "base",
    )

    command.upgrade(
        alembic_config,
        "head",
    )

    engine.dispose()

    yield

    engine.dispose()


@pytest.fixture(autouse=True)
def clean_test_database() -> Generator[None, None, None]:
    yield

    if not settings.db_name.endswith("_test"):
        return

    engine.dispose()

    with engine.begin() as connection:
        connection.execute(
            text(
                """
                TRUNCATE TABLE
                    security_findings,
                    security_signals,
                    security_rules,
                    runbooks,
                    telemetry,
                    alerts,
                    alert_rules,
                    checks,
                    agents,
                    hosts
                RESTART IDENTITY
                CASCADE
                """
            )
        )

    engine.dispose()