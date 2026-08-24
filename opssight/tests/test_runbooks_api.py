from collections.abc import Generator
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
from opssight.models.runbook import Runbook


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


def create_test_runbook(
    session: Session,
) -> Runbook:
    suffix = uuid4().hex[:8]

    runbook = Runbook(
        name=f"test-runbook-{suffix}",
        description="investigate a security incident",
        instructions="review the evidence and validate the activity",
    )

    session.add(runbook)
    session.flush()

    return runbook


def test_runbooks_requires_authentication() -> None:
    client = TestClient(app)

    response = client.get(
        "/api/v1/runbooks"
    )

    assert response.status_code == 401


def test_viewer_can_list_runbooks(
    session: Session,
) -> None:
    runbook = create_test_runbook(
        session
    )

    override_database_session(
        session
    )
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/runbooks"
    )

    assert response.status_code == 200

    matching_runbooks = [
        item
        for item in response.json()
        if item["id"] == str(runbook.id)
    ]

    assert len(matching_runbooks) == 1
    assert matching_runbooks[0]["name"] == runbook.name


def test_viewer_can_get_runbook(
    session: Session,
) -> None:
    runbook = create_test_runbook(
        session
    )

    override_database_session(
        session
    )
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        f"/api/v1/runbooks/{runbook.id}"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["id"] == str(runbook.id)
    assert body["name"] == runbook.name
    assert body["description"] == runbook.description
    assert body["instructions"] == runbook.instructions


def test_missing_runbook_returns_404(
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
        f"/api/v1/runbooks/{uuid4()}"
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "runbook not found"
    }


def test_invalid_runbook_id_returns_422() -> None:
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/runbooks/not-a-uuid"
    )

    assert response.status_code == 422


def test_viewer_cannot_create_runbook() -> None:
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/runbooks",
        json={
            "name": "forbidden-runbook",
            "description": "description",
            "instructions": "instructions",
        },
    )

    assert response.status_code == 403


def test_admin_can_create_runbook(
    session: Session,
) -> None:
    name = f"created-runbook-{uuid4().hex[:8]}"

    override_database_session(
        session
    )
    override_role(
        Role.ADMIN
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/runbooks",
        json={
            "name": name,
            "description": "investigate suspicious activity",
            "instructions": "review the evidence and escalate if needed",
        },
    )

    assert response.status_code == 201

    body = response.json()

    assert body["name"] == name
    assert body["description"] == (
        "investigate suspicious activity"
    )
    assert body["instructions"] == (
        "review the evidence and escalate if needed"
    )
    assert "created_at" in body

    stored_runbook = session.get(
        Runbook,
        body["id"],
    )

    assert stored_runbook is not None

    session.delete(
        stored_runbook
    )
    session.commit()


def test_duplicate_runbook_name_returns_409(
    session: Session,
) -> None:
    name = f"duplicate-runbook-{uuid4().hex[:8]}"

    existing_runbook = Runbook(
        name=name,
        description="existing description",
        instructions="existing instructions",
    )

    session.add(
        existing_runbook
    )
    session.commit()

    override_database_session(
        session
    )
    override_role(
        Role.ADMIN
    )

    client = TestClient(app)

    response = client.post(
        "/api/v1/runbooks",
        json={
            "name": name,
            "description": "new description",
            "instructions": "new instructions",
        },
    )

    assert response.status_code == 409
    assert response.json() == {
        "detail": "runbook name already exists"
    }

    stored_runbook = session.get(
        Runbook,
        existing_runbook.id,
    )

    assert stored_runbook is not None

    session.delete(
        stored_runbook
    )
    session.commit()


@pytest.mark.parametrize(
    "field",
    [
        "name",
        "description",
        "instructions",
    ],
)
def test_create_runbook_rejects_blank_fields(
    field: str,
) -> None:
    override_role(
        Role.ADMIN
    )

    payload = {
        "name": "valid-runbook",
        "description": "valid description",
        "instructions": "valid instructions",
    }

    payload[field] = "   "

    client = TestClient(app)

    response = client.post(
        "/api/v1/runbooks",
        json=payload,
    )

    assert response.status_code == 422