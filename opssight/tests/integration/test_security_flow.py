from datetime import UTC, datetime, timedelta
from uuid import uuid4

from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.api.dependencies import get_session
from opssight.auth.dependencies import get_current_principal
from opssight.auth.models import Principal, Role
from opssight.main import app
from opssight.models.enums import (
    AlertSeverity,
    SecurityFindingStatus,
)
from opssight.models.runbook import Runbook
from opssight.models.security_finding import SecurityFinding
from opssight.models.security_rule import SecurityRule
from opssight.models.security_signal import SecuritySignal
from opssight.security.engine import process_security_signal


def test_security_signal_to_resolved_finding_flow(
    integration_session: Session,
) -> None:
    suffix = uuid4().hex[:8]
    signal_key = f"user:integration-{suffix}"

    runbook = Runbook(
        name=f"integration-runbook-{suffix}",
        description="investigate repeated authentication failures",
        instructions=(
            "review the account, inspect the source, "
            "and escalate suspicious activity"
        ),
    )

    rule = SecurityRule(
        runbook=runbook,
        name=f"integration-security-rule-{suffix}",
        signal_type="authentication_failure",
        severity=AlertSeverity.CRITICAL.value,
        threshold=3,
        window_seconds=600,
    )

    integration_session.add(runbook)
    integration_session.flush()

    start = datetime(
        2026,
        8,
        24,
        12,
        0,
        tzinfo=UTC,
    )

    for offset in range(3):
        process_security_signal(
            integration_session,
            signal_type="authentication_failure",
            signal_key=signal_key,
            message="failed ssh login",
            observed_at=start + timedelta(minutes=offset),
            attributes={
                "protocol": "ssh",
                "username": f"integration-{suffix}",
            },
        )

    finding = integration_session.scalar(
        select(SecurityFinding).where(
            SecurityFinding.security_rule_id == rule.id,
            SecurityFinding.signal_key == signal_key,
        )
    )

    assert finding is not None
    assert finding.status == SecurityFindingStatus.OPEN.value
    assert finding.occurrence_count == 3
    assert finding.runbook_id == runbook.id
    assert finding.severity == AlertSeverity.CRITICAL.value

    def override_session():
        yield integration_session

    def override_viewer() -> Principal:
        return Principal(
            subject="integration-viewer",
            role=Role.VIEWER,
        )

    app.dependency_overrides[
        get_session
    ] = override_session

    app.dependency_overrides[
        get_current_principal
    ] = override_viewer

    client = TestClient(app)

    get_response = client.get(
        f"/api/v1/security/findings/{finding.id}"
    )

    assert get_response.status_code == 200

    body = get_response.json()

    assert body["id"] == str(finding.id)
    assert body["status"] == SecurityFindingStatus.OPEN.value
    assert body["occurrence_count"] == 3
    assert body["runbook_id"] == str(runbook.id)

    runbook_response = client.get(
        f"/api/v1/runbooks/{runbook.id}"
    )

    assert runbook_response.status_code == 200
    assert runbook_response.json()["id"] == str(runbook.id)
    assert (
        runbook_response.json()["instructions"]
        == runbook.instructions
    )

    forbidden_response = client.post(
        f"/api/v1/security/findings/{finding.id}/resolve"
    )

    assert forbidden_response.status_code == 403

    integration_session.refresh(
        finding
    )

    assert finding.status == SecurityFindingStatus.OPEN.value
    assert finding.resolved_at is None

    def override_operator() -> Principal:
        return Principal(
            subject="integration-operator",
            role=Role.OPERATOR,
        )

    app.dependency_overrides[
        get_current_principal
    ] = override_operator

    resolve_response = client.post(
        f"/api/v1/security/findings/{finding.id}/resolve"
    )

    assert resolve_response.status_code == 200

    resolved_body = resolve_response.json()

    assert (
        resolved_body["status"]
        == SecurityFindingStatus.RESOLVED.value
    )
    assert resolved_body["resolved_at"] is not None

    integration_session.refresh(
        finding
    )

    assert (
        finding.status
        == SecurityFindingStatus.RESOLVED.value
    )
    assert finding.resolved_at is not None

    app.dependency_overrides.clear()

    signals = list(
        integration_session.scalars(
            select(SecuritySignal).where(
                SecuritySignal.signal_key == signal_key
            )
        ).all()
    )

    integration_session.delete(
        finding
    )

    for signal in signals:
        integration_session.delete(
            signal
        )

    integration_session.delete(
        rule
    )
    integration_session.delete(
        runbook
    )

    integration_session.commit()