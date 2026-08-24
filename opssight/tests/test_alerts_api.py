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
from opssight.models.alert import Alert
from opssight.models.alert_rule import AlertRule
from opssight.models.check import Check
from opssight.models.enums import AlertSeverity, AlertStatus
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


def override_database_session(
    session: Session,
) -> None:
    def override_session() -> Generator[Session, None, None]:
        yield session

    app.dependency_overrides[
        get_session
    ] = override_session


def create_test_alert(
    session: Session,
) -> Alert:
    suffix = uuid4().hex[:8]

    host = Host(
        name=f"alert-host-{suffix}",
        address="127.0.0.1",
    )

    check = Check(
        host=host,
        name=f"http-check-{suffix}",
        check_type="http",
        target="https://example.com",
    )

    rule = AlertRule(
        check=check,
        name=f"http-failure-rule-{suffix}",
        severity=AlertSeverity.CRITICAL.value,
        failure_threshold=1,
    )

    alert = Alert(
        alert_rule=rule,
        status=AlertStatus.OPEN.value,
        message="http check failed",
        opened_at=datetime.now(UTC),
    )

    session.add(host)
    session.flush()

    return alert


def test_alerts_requires_authentication() -> None:
    client = TestClient(app)

    response = client.get(
        "/api/v1/alerts"
    )

    assert response.status_code == 401


def test_viewer_can_list_alerts(
    session: Session,
) -> None:
    alert = create_test_alert(
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
        "/api/v1/alerts"
    )

    assert response.status_code == 200

    matching_alerts = [
        item
        for item in response.json()
        if item["id"] == str(alert.id)
    ]

    assert len(matching_alerts) == 1

    returned_alert = matching_alerts[0]

    assert returned_alert["status"] == AlertStatus.OPEN.value
    assert returned_alert["message"] == "http check failed"
    assert returned_alert["recovered_at"] is None


def test_viewer_can_get_alert(
    session: Session,
) -> None:
    alert = create_test_alert(
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
        f"/api/v1/alerts/{alert.id}"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["id"] == str(alert.id)
    assert body["alert_rule_id"] == str(alert.alert_rule_id)
    assert body["status"] == AlertStatus.OPEN.value


def test_missing_alert_returns_404(
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
        f"/api/v1/alerts/{uuid4()}"
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "alert not found"
    }


def test_invalid_alert_id_returns_422() -> None:
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/alerts/not-a-uuid"
    )

    assert response.status_code == 422


@pytest.mark.parametrize(
    "role",
    [
        Role.VIEWER,
        Role.OPERATOR,
        Role.ADMIN,
    ],
)
def test_authorized_roles_can_list_alerts(
    session: Session,
    role: Role,
) -> None:
    override_database_session(
        session
    )
    override_role(
        role
    )

    client = TestClient(app)

    response = client.get(
        "/api/v1/alerts"
    )

    assert response.status_code == 200