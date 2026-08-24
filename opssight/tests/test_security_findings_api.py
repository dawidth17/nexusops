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
from opssight.models.enums import (
    AlertSeverity,
    SecurityFindingStatus,
)
from opssight.models.runbook import Runbook
from opssight.models.security_finding import SecurityFinding
from opssight.models.security_rule import SecurityRule


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


def create_test_finding(
    session: Session,
) -> tuple[
    SecurityFinding,
    SecurityRule,
    Runbook,
]:
    suffix = uuid4().hex[:8]
    observed_at = datetime.now(UTC)

    runbook = Runbook(
        name=f"security-runbook-{suffix}",
        description="investigate repeated authentication failures",
        instructions="review the account and validate the activity",
    )

    rule = SecurityRule(
        runbook=runbook,
        name=f"security-rule-{suffix}",
        signal_type="authentication_failure",
        severity=AlertSeverity.CRITICAL.value,
        threshold=3,
        window_seconds=600,
    )

    finding = SecurityFinding(
        security_rule=rule,
        runbook=runbook,
        signal_key=f"user:{suffix}",
        severity=AlertSeverity.CRITICAL.value,
        status=SecurityFindingStatus.OPEN.value,
        summary="repeated authentication failures",
        occurrence_count=3,
        first_seen_at=observed_at,
        last_seen_at=observed_at,
    )

    session.add(runbook)
    session.flush()

    return finding, rule, runbook


def test_security_findings_requires_authentication() -> None:
    client = TestClient(app)

    response = client.get(
        "/api/v1/security/findings"
    )

    assert response.status_code == 401


def test_viewer_can_list_security_findings(
    session: Session,
) -> None:
    finding, _, _ = create_test_finding(
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
        "/api/v1/security/findings"
    )

    assert response.status_code == 200

    matching_findings = [
        item
        for item in response.json()
        if item["id"] == str(finding.id)
    ]

    assert len(matching_findings) == 1

    returned_finding = matching_findings[0]

    assert (
        returned_finding["status"]
        == SecurityFindingStatus.OPEN.value
    )
    assert returned_finding["occurrence_count"] == 3
    assert returned_finding["runbook_id"] == str(
        finding.runbook_id
    )


def test_viewer_can_get_security_finding(
    session: Session,
) -> None:
    finding, _, _ = create_test_finding(
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
        f"/api/v1/security/findings/{finding.id}"
    )

    assert response.status_code == 200

    body = response.json()

    assert body["id"] == str(finding.id)
    assert body["signal_key"] == finding.signal_key
    assert body["severity"] == finding.severity


def test_missing_security_finding_returns_404(
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
        f"/api/v1/security/findings/{uuid4()}"
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "security finding not found"
    }


def test_viewer_cannot_resolve_security_finding(
    session: Session,
) -> None:
    finding, _, _ = create_test_finding(
        session
    )

    override_database_session(
        session
    )
    override_role(
        Role.VIEWER
    )

    client = TestClient(app)

    response = client.post(
        f"/api/v1/security/findings/{finding.id}/resolve"
    )

    assert response.status_code == 403

    session.refresh(
        finding
    )

    assert (
        finding.status
        == SecurityFindingStatus.OPEN.value
    )
    assert finding.resolved_at is None


def test_operator_can_resolve_security_finding(
    session: Session,
) -> None:
    finding, rule, runbook = create_test_finding(
        session
    )

    override_database_session(
        session
    )
    override_role(
        Role.OPERATOR
    )

    client = TestClient(app)

    response = client.post(
        f"/api/v1/security/findings/{finding.id}/resolve"
    )

    assert response.status_code == 200

    body = response.json()

    assert (
        body["status"]
        == SecurityFindingStatus.RESOLVED.value
    )
    assert body["resolved_at"] is not None

    session.refresh(
        finding
    )

    assert (
        finding.status
        == SecurityFindingStatus.RESOLVED.value
    )
    assert finding.resolved_at is not None

    session.delete(
        finding
    )
    session.delete(
        rule
    )
    session.delete(
        runbook
    )
    session.commit()


def test_resolve_is_idempotent(
    session: Session,
) -> None:
    finding, rule, runbook = create_test_finding(
        session
    )

    override_database_session(
        session
    )
    override_role(
        Role.OPERATOR
    )

    client = TestClient(app)

    first_response = client.post(
        f"/api/v1/security/findings/{finding.id}/resolve"
    )

    assert first_response.status_code == 200

    first_resolved_at = first_response.json()[
        "resolved_at"
    ]

    second_response = client.post(
        f"/api/v1/security/findings/{finding.id}/resolve"
    )

    assert second_response.status_code == 200
    assert (
        second_response.json()["resolved_at"]
        == first_resolved_at
    )

    session.delete(
        finding
    )
    session.delete(
        rule
    )
    session.delete(
        runbook
    )
    session.commit()


def test_missing_finding_cannot_be_resolved(
    session: Session,
) -> None:
    override_database_session(
        session
    )
    override_role(
        Role.OPERATOR
    )

    client = TestClient(app)

    response = client.post(
        f"/api/v1/security/findings/{uuid4()}/resolve"
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "security finding not found"
    }


def test_admin_can_resolve_security_finding(
    session: Session,
) -> None:
    finding, rule, runbook = create_test_finding(
        session
    )

    override_database_session(
        session
    )
    override_role(
        Role.ADMIN
    )

    client = TestClient(app)

    response = client.post(
        f"/api/v1/security/findings/{finding.id}/resolve"
    )

    assert response.status_code == 200
    assert (
        response.json()["status"]
        == SecurityFindingStatus.RESOLVED.value
    )

    session.delete(
        finding
    )
    session.delete(
        rule
    )
    session.delete(
        runbook
    )
    session.commit()