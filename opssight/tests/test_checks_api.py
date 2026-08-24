from collections.abc import Generator
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import get_current_principal
from opssight.auth.models import Principal, Role
from opssight.config import settings
from opssight.database import SessionFactory, engine
from opssight.main import app
from opssight.models.check import Check
from opssight.models.host import Host


pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="api tests require a dedicated test database",
)


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


def override_database_session(
    session: Session,
) -> None:
    def override_session() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[
        get_session
    ] = override_session


def create_test_host(
    session: Session,
) -> Host:
    host = Host(
        name=f"checks-api-host-{uuid4().hex[:8]}",
        address="127.0.0.1",
        created_at=datetime.now(UTC),
    )

    session.add(host)
    session.flush()

    return host


def test_checks_requires_authentication() -> None:
    client = TestClient(app)

    response = client.get(
        "/api/v1/checks"
    )

    assert response.status_code == 401


def test_viewer_can_list_checks(
    session: Session,
) -> None:
    host = create_test_host(
        session
    )

    check = Check(
        host_id=host.id,
        name=f"dns-{uuid4().hex[:8]}",
        check_type="dns",
        target="localhost",
        interval_seconds=60,
        timeout_seconds=5,
    )

    session.add(check)
    session.flush()

    override_database_session(
        session
    )
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/checks"
    )

    assert response.status_code == 200

    matching_checks = [
        item
        for item in response.json()
        if item["id"] == str(check.id)
    ]

    assert len(matching_checks) == 1
    assert matching_checks[0]["name"] == check.name
    assert matching_checks[0]["check_type"] == "dns"


def test_checks_can_be_filtered_by_host(
    session: Session,
) -> None:
    first_host = create_test_host(
        session
    )
    second_host = create_test_host(
        session
    )

    first_check = Check(
        host_id=first_host.id,
        name=f"first-{uuid4().hex[:8]}",
        check_type="dns",
        target="localhost",
    )

    second_check = Check(
        host_id=second_host.id,
        name=f"second-{uuid4().hex[:8]}",
        check_type="dns",
        target="localhost",
    )

    session.add_all(
        [
            first_check,
            second_check,
        ]
    )
    session.flush()

    override_database_session(
        session
    )
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/checks",
        params={
            "host_id": str(first_host.id),
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert len(body) == 1
    assert body[0]["id"] == str(first_check.id)
    assert body[0]["host_id"] == str(first_host.id)


def test_viewer_can_get_check(
    session: Session,
) -> None:
    host = create_test_host(
        session
    )

    check = Check(
        host_id=host.id,
        name=f"tcp-{uuid4().hex[:8]}",
        check_type="tcp",
        target="127.0.0.1:5432",
    )

    session.add(check)
    session.flush()

    override_database_session(
        session
    )
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        f"/api/v1/checks/{check.id}"
    )

    assert response.status_code == 200
    assert response.json()["id"] == str(check.id)


def test_missing_check_returns_404(
    session: Session,
) -> None:
    override_database_session(
        session
    )
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        f"/api/v1/checks/{uuid4()}"
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "check not found"
    }


def test_invalid_check_id_returns_422() -> None:
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/checks/not-a-uuid"
    )

    assert response.status_code == 422


def test_viewer_cannot_create_check() -> None:
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/checks",
        json={
            "host_id": str(uuid4()),
            "name": "forbidden-check",
            "check_type": "dns",
            "target": "localhost",
        },
    )

    assert response.status_code == 403


def test_admin_cannot_create_check_for_missing_host(
    session: Session,
) -> None:
    override_database_session(
        session
    )
    override_role(
        Role.ADMIN
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/checks",
        json={
            "host_id": str(uuid4()),
            "name": "missing-host-check",
            "check_type": "dns",
            "target": "localhost",
        },
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "host not found"
    }


def test_admin_can_create_check(
    session: Session,
) -> None:
    host = create_test_host(
        session
    )

    check_name = f"http-{uuid4().hex[:8]}"

    override_database_session(
        session
    )
    override_role(
        Role.ADMIN
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/checks",
        json={
            "host_id": str(host.id),
            "name": check_name,
            "check_type": "http",
            "target": "https://example.com",
            "interval_seconds": 120,
            "timeout_seconds": 10,
        },
    )

    assert response.status_code == 201

    body = response.json()

    assert body["host_id"] == str(host.id)
    assert body["name"] == check_name
    assert body["check_type"] == "http"
    assert body["target"] == "https://example.com"
    assert body["interval_seconds"] == 120
    assert body["timeout_seconds"] == 10
    assert body["consecutive_failures"] == 0
    assert body["enabled"] is True

    stored_check = session.get(
        Check,
        body["id"],
    )

    assert stored_check is not None

    session.delete(
        stored_check
    )
    session.delete(
        host
    )
    session.commit()


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("check_type", "invalid"),
        ("interval_seconds", 0),
        ("timeout_seconds", 0),
    ],
)
def test_create_check_validates_input(
    field: str,
    value: str | int,
) -> None:
    override_role(
        Role.ADMIN
    )

    payload: dict[str, object] = {
        "host_id": str(uuid4()),
        "name": "invalid-check",
        "check_type": "dns",
        "target": "localhost",
        "interval_seconds": 60,
        "timeout_seconds": 5,
    }

    payload[field] = value

    client = TestClient(app)

    response = client.post(
        "/api/v1/checks",
        json=payload,
    )

    assert response.status_code == 422