from collections.abc import Generator
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from opssight.config import settings
from opssight.database import SessionFactory
from opssight.models.host import Host
from opssight.repositories.agent_repository import (
    create_agent,
    get_agent_by_agent_id,
    mark_agent_seen,
)

pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="repository tests require a dedicated test database",
)


@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


def create_test_host(
    session: Session,
) -> Host:
    host = Host(
        name=f"agent-host-{uuid4().hex[:8]}",
        address="10.0.0.50",
    )

    session.add(host)
    session.flush()

    return host


def test_creates_and_resolves_agent(
    session: Session,
) -> None:
    host = create_test_host(
        session
    )

    agent_id = (
        f"sentinel-{uuid4().hex}"
    )

    created = create_agent(
        session,
        agent_id=agent_id,
        host_id=host.id,
        version="0.9.0",
    )

    resolved = get_agent_by_agent_id(
        session,
        agent_id,
    )

    assert resolved is not None
    assert resolved.id == created.id
    assert resolved.agent_id == agent_id
    assert resolved.host_id == host.id
    assert resolved.version == "0.9.0"
    assert resolved.host.id == host.id


def test_agent_id_is_unique(
    session: Session,
) -> None:
    first_host = create_test_host(
        session
    )

    second_host = create_test_host(
        session
    )

    agent_id = (
        f"sentinel-{uuid4().hex}"
    )

    create_agent(
        session,
        agent_id=agent_id,
        host_id=first_host.id,
    )

    with pytest.raises(
        IntegrityError
    ):
        create_agent(
            session,
            agent_id=agent_id,
            host_id=second_host.id,
        )

    session.rollback()


def test_marks_agent_as_seen(
    session: Session,
) -> None:
    host = create_test_host(
        session
    )

    agent = create_agent(
        session,
        agent_id=f"sentinel-{uuid4().hex}",
        host_id=host.id,
    )

    seen_at = datetime.now(UTC)

    updated = mark_agent_seen(
        session,
        agent,
        seen_at=seen_at,
        version="0.9.0",
        last_config_version="config-v1",
    )

    assert updated.last_seen_at == seen_at
    assert updated.version == "0.9.0"
    assert (
        updated.last_config_version
        == "config-v1"
    )