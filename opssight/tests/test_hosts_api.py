from collections.abc import Generator
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import get_current_principal
from opssight.auth.models import Principal, Role
from opssight.config import settings
from opssight.database import SessionFactory, engine
from opssight.main import app
from opssight.models.host import Host


pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="api tests require a dedicated test database",
)


@pytest.fixture(scope="module", autouse=True)
def migrate_database() -> Generator[None, None, None]:
    alembic_config = Config("alembic.ini")

    command.downgrade(
        alembic_config,
        "base",
    )
    command.upgrade(
        alembic_config,
        "head",
    )

    yield

    engine.dispose()


@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


@pytest.fixture(autouse=True)
def clear_dependency_overrides() -> Generator[None, None, None]:
    app.dependency_overrides.clear()

    yield

    app.dependency_overrides.clear()


def override_role(
    role: Role,
) -> None:
    def override_principal() -> Principal:
        return Principal(
            subject=f"{role.name.lower()}-user",
            role=role,
        )

    app.dependency_overrides[
        get_current_principal
    ] = override_principal


def test_hosts_requires_authentication() -> None:
    client = TestClient(app)

    response = client.get(
        "/api/v1/hosts"
    )

    assert response.status_code == 401


def test_viewer_can_list_hosts(
    session: Session,
) -> None:
    host = Host(
        name=f"api-host-{uuid4().hex[:8]}",
        address="10.0.0.10",
        enabled=True,
        created_at=datetime.now(UTC),
    )

    session.add(host)
    session.flush()

    def override_session() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[
        get_session
    ] = override_session

    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/hosts"
    )

    assert response.status_code == 200

    matching_hosts = [
        item
        for item in response.json()
        if item["id"] == str(host.id)
    ]

    assert len(matching_hosts) == 1
    assert matching_hosts[0]["name"] == host.name
    assert matching_hosts[0]["address"] == host.address


@pytest.mark.parametrize(
    "role",
    [
        Role.VIEWER,
        Role.OPERATOR,
        Role.ADMIN,
    ],
)
def test_authorized_roles_can_list_hosts(
    session: Session,
    role: Role,
) -> None:
    def override_session() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[
        get_session
    ] = override_session

    override_role(
        role
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/hosts"
    )

    assert response.status_code == 200


def test_viewer_can_get_host(
    session: Session,
) -> None:
    host = Host(
        name=f"single-host-{uuid4().hex[:8]}",
        address="10.0.0.20",
        enabled=True,
        created_at=datetime.now(UTC),
    )

    session.add(host)
    session.flush()

    def override_session() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[
        get_session
    ] = override_session

    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        f"/api/v1/hosts/{host.id}"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["id"] == str(host.id)
    assert body["name"] == host.name
    assert body["address"] == host.address


def test_missing_host_returns_404(
    session: Session,
) -> None:
    def override_session() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[
        get_session
    ] = override_session

    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        f"/api/v1/hosts/{uuid4()}"
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "host not found"
    }


def test_invalid_host_id_returns_422() -> None:
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/hosts/not-a-uuid"
    )

    assert response.status_code == 422


def test_viewer_cannot_create_host() -> None:
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/hosts",
        json={
            "name": "forbidden-host",
            "address": "10.0.0.30",
        },
    )

    assert response.status_code == 403


def test_admin_can_create_host(
    session: Session,
) -> None:
    name = f"created-host-{uuid4().hex[:8]}"

    def override_session() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[
        get_session
    ] = override_session

    override_role(
        Role.ADMIN
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/hosts",
        json={
            "name": name,
            "address": "10.0.0.40",
        },
    )

    assert response.status_code == 201

    body = response.json()

    assert body["name"] == name
    assert body["address"] == "10.0.0.40"
    assert body["enabled"] is True

    stored_host = session.scalar(
        select(Host).where(
            Host.id == body["id"]
        )
    )

    assert stored_host is not None
    assert stored_host.name == name

    session.delete(
        stored_host
    )
    session.commit()


def test_create_host_validates_input() -> None:
    override_role(
        Role.ADMIN
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/hosts",
        json={
            "name": "   ",
            "address": "",
        },
    )

    assert response.status_code == 422