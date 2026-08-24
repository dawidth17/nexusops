from collections.abc import Generator
from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from opssight.config import settings
from opssight.database import SessionFactory, engine
from opssight.models.enums import (
    AlertSeverity,
    SecurityFindingStatus,
)
from opssight.models.runbook import Runbook
from opssight.models.security_finding import SecurityFinding
from opssight.models.security_rule import SecurityRule
from opssight.models.security_signal import SecuritySignal
from opssight.security.engine import (
    process_security_signal,
    resolve_security_finding,
)


pytestmark = pytest.mark.skipif(
    not settings.db_name.endswith("_test"),
    reason="security tests require a dedicated test database",
)



@pytest.fixture
def session() -> Generator[Session, None, None]:
    with SessionFactory() as database_session:
        yield database_session
        database_session.rollback()


def create_failed_login_rule(
    session: Session,
    threshold: int = 3,
) -> SecurityRule:
    suffix = uuid4().hex[:8]

    runbook = Runbook(
        name=f"failed-login-runbook-{suffix}",
        description="respond to repeated authentication failures",
        instructions=(
            "review the account and source address, "
            "validate activity, and escalate if needed"
        ),
    )

    rule = SecurityRule(
        runbook=runbook,
        name=f"failed-login-rule-{suffix}",
        signal_type="authentication_failure",
        severity=AlertSeverity.CRITICAL.value,
        threshold=threshold,
        window_seconds=600,
    )

    session.add(runbook)
    session.flush()

    return rule


def test_signal_below_threshold_does_not_create_finding(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=3,
    )

    observed_at = datetime.now(UTC)

    _, changed_findings = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="failed ssh login",
        observed_at=observed_at,
    )

    assert changed_findings == []

    finding = session.scalar(
        select(SecurityFinding).where(
            SecurityFinding.security_rule_id == rule.id
        )
    )

    assert finding is None


def test_threshold_creates_finding(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=3,
    )

    start = datetime.now(UTC)

    for offset in range(3):
        process_security_signal(
            session,
            signal_type="authentication_failure",
            signal_key="user:alice",
            message="failed ssh login",
            observed_at=start + timedelta(minutes=offset),
        )

    finding = session.scalar(
        select(SecurityFinding).where(
            SecurityFinding.security_rule_id == rule.id,
            SecurityFinding.signal_key == "user:alice",
        )
    )

    assert finding is not None
    assert finding.status == SecurityFindingStatus.OPEN.value
    assert finding.occurrence_count == 3
    assert finding.severity == AlertSeverity.CRITICAL.value
    assert finding.runbook_id == rule.runbook_id
    assert finding.first_seen_at == start
    assert finding.last_seen_at == start + timedelta(minutes=2)


def test_open_finding_aggregates_new_signals(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=2,
    )

    start = datetime.now(UTC)

    process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="first failure",
        observed_at=start,
    )

    process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="second failure",
        observed_at=start + timedelta(minutes=1),
    )

    _, changed_findings = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="third failure",
        observed_at=start + timedelta(minutes=2),
    )

    assert len(changed_findings) == 1

    finding = changed_findings[0]

    assert finding.occurrence_count == 3
    assert finding.summary == "third failure"
    assert finding.last_seen_at == start + timedelta(minutes=2)

    findings = list(
        session.scalars(
            select(SecurityFinding).where(
                SecurityFinding.security_rule_id == rule.id
            )
        ).all()
    )

    assert len(findings) == 1


def test_signal_keys_create_independent_findings(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=2,
    )

    start = datetime.now(UTC)

    for signal_key in (
        "user:alice",
        "user:bob",
    ):
        process_security_signal(
            session,
            signal_type="authentication_failure",
            signal_key=signal_key,
            message="failed ssh login",
            observed_at=start,
        )

        process_security_signal(
            session,
            signal_type="authentication_failure",
            signal_key=signal_key,
            message="failed ssh login",
            observed_at=start + timedelta(minutes=1),
        )

    findings = list(
        session.scalars(
            select(SecurityFinding)
            .where(
                SecurityFinding.security_rule_id == rule.id
            )
            .order_by(SecurityFinding.signal_key)
        ).all()
    )

    assert len(findings) == 2

    assert findings[0].signal_key == "user:alice"
    assert findings[0].occurrence_count == 2

    assert findings[1].signal_key == "user:bob"
    assert findings[1].occurrence_count == 2


def test_signals_outside_window_do_not_reach_threshold(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=3,
    )

    start = datetime.now(UTC)

    process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="old failure",
        observed_at=start,
    )

    process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="recent failure",
        observed_at=start + timedelta(minutes=20),
    )

    process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="recent failure",
        observed_at=start + timedelta(minutes=21),
    )

    finding = session.scalar(
        select(SecurityFinding).where(
            SecurityFinding.security_rule_id == rule.id
        )
    )

    assert finding is None


def test_disabled_rule_is_ignored(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=1,
    )

    rule.enabled = False
    session.flush()

    _, changed_findings = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="failed ssh login",
        observed_at=datetime.now(UTC),
    )

    assert changed_findings == []

    finding = session.scalar(
        select(SecurityFinding).where(
            SecurityFinding.security_rule_id == rule.id
        )
    )

    assert finding is None


def test_security_signal_is_persisted(
    session: Session,
) -> None:
    create_failed_login_rule(
        session,
        threshold=10,
    )

    observed_at = datetime.now(UTC)

    signal, _ = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="failed ssh login",
        observed_at=observed_at,
        attributes={
            "username": "alice",
            "protocol": "ssh",
        },
    )

    stored_signal = session.get(
        SecuritySignal,
        signal.id,
    )

    assert stored_signal is not None
    assert stored_signal.signal_type == "authentication_failure"
    assert stored_signal.signal_key == "user:alice"
    assert stored_signal.observed_at == observed_at
    assert stored_signal.attributes == {
        "username": "alice",
        "protocol": "ssh",
    }


def test_finding_can_be_resolved(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=1,
    )

    observed_at = datetime.now(UTC)

    _, changed_findings = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="failed ssh login",
        observed_at=observed_at,
    )

    finding = changed_findings[0]
    resolved_at = observed_at + timedelta(minutes=15)

    resolved_finding = resolve_security_finding(
        session,
        finding.id,
        resolved_at,
    )

    assert resolved_finding.id == finding.id
    assert (
        resolved_finding.status
        == SecurityFindingStatus.RESOLVED.value
    )
    assert resolved_finding.resolved_at == resolved_at
    assert resolved_finding.runbook_id == rule.runbook_id


def test_resolving_finding_twice_is_idempotent(
    session: Session,
) -> None:
    create_failed_login_rule(
        session,
        threshold=1,
    )

    observed_at = datetime.now(UTC)

    _, changed_findings = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="failed ssh login",
        observed_at=observed_at,
    )

    finding = changed_findings[0]

    first_resolved_at = observed_at + timedelta(minutes=10)

    resolve_security_finding(
        session,
        finding.id,
        first_resolved_at,
    )

    resolve_security_finding(
        session,
        finding.id,
        observed_at + timedelta(minutes=20),
    )

    assert (
        finding.status
        == SecurityFindingStatus.RESOLVED.value
    )

    assert finding.resolved_at == first_resolved_at


def test_new_finding_can_open_after_resolution(
    session: Session,
) -> None:
    rule = create_failed_login_rule(
        session,
        threshold=1,
    )

    first_signal_time = datetime.now(UTC)

    _, first_changes = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="first incident",
        observed_at=first_signal_time,
    )

    first_finding = first_changes[0]

    resolve_security_finding(
        session,
        first_finding.id,
        first_signal_time + timedelta(minutes=5),
    )

    _, second_changes = process_security_signal(
        session,
        signal_type="authentication_failure",
        signal_key="user:alice",
        message="second incident",
        observed_at=first_signal_time + timedelta(minutes=20),
    )

    assert len(second_changes) == 1

    findings = list(
        session.scalars(
            select(SecurityFinding)
            .where(
                SecurityFinding.security_rule_id == rule.id,
                SecurityFinding.signal_key == "user:alice",
            )
            .order_by(SecurityFinding.first_seen_at)
        ).all()
    )

    assert len(findings) == 2
    assert (
        findings[0].status
        == SecurityFindingStatus.RESOLVED.value
    )
    assert (
        findings[1].status
        == SecurityFindingStatus.OPEN.value
    )


def test_missing_finding_is_rejected(
    session: Session,
) -> None:
    missing_finding_id = uuid4()

    with pytest.raises(
        ValueError,
        match=(
            f"security finding {missing_finding_id} "
            "was not found"
        ),
    ):
        resolve_security_finding(
            session,
            missing_finding_id,
            datetime.now(UTC),
        )