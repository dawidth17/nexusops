from collections.abc import Generator

import pytest
from sqlalchemy.orm import Session

from opssight.config import settings
from opssight.database import SessionFactory


@pytest.fixture
def integration_session() -> Generator[Session, None, None]:
    if not settings.db_name.endswith("_test"):
        pytest.skip(
            "integration tests require a dedicated test database"
        )

    with SessionFactory() as session:
        yield session
        session.rollback()