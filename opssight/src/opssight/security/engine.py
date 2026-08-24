from datetime import datetime, timedelta
from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from opssight.models.enums import SecurityFindingStatus
from opssight.models.security_finding import SecurityFinding
from opssight.models.security_rule import SecurityRule
from opssight.models.security_signal import SecuritySignal


def process_security_signal(
    session: Session,
    signal_type: str,
    signal_key: str,
    message: str,
    observed_at: datetime,
    host_id: UUID | None = None,
    attributes: dict[str, str] | None = None,
) -> tuple[SecuritySignal, list[SecurityFinding]]:
    signal = SecuritySignal(
        host_id=host_id,
        signal_type=signal_type,
        signal_key=signal_key,
        message=message,
        attributes=attributes or {},
        observed_at=observed_at,
    )

    session.add(signal)
    session.flush()

    rules = list(
        session.scalars(
            select(SecurityRule)
            .where(
                SecurityRule.signal_type == signal.signal_type,
                SecurityRule.enabled.is_(True),
            )
            .order_by(SecurityRule.name)
            .with_for_update()
        ).all()
    )

    changed_findings: list[SecurityFinding] = []

    for rule in rules:
        open_finding = session.scalar(
            select(SecurityFinding)
            .where(
                SecurityFinding.security_rule_id == rule.id,
                SecurityFinding.signal_key == signal.signal_key,
                SecurityFinding.status
                == SecurityFindingStatus.OPEN.value,
            )
            .with_for_update()
        )

        if open_finding is not None:
            open_finding.occurrence_count += 1

            if signal.observed_at < open_finding.first_seen_at:
                open_finding.first_seen_at = signal.observed_at

            if signal.observed_at > open_finding.last_seen_at:
                open_finding.last_seen_at = signal.observed_at

            open_finding.summary = signal.message

            changed_findings.append(open_finding)
            continue

        window_start = signal.observed_at - timedelta(
            seconds=rule.window_seconds
        )

        count_statement = select(
            func.count(SecuritySignal.id),
            func.min(SecuritySignal.observed_at),
        ).where(
            SecuritySignal.signal_type == rule.signal_type,
            SecuritySignal.signal_key == signal.signal_key,
            SecuritySignal.observed_at >= window_start,
            SecuritySignal.observed_at <= signal.observed_at,
        )

        occurrence_count, first_seen_at = session.execute(
            count_statement
        ).one()

        if occurrence_count < rule.threshold:
            continue

        if first_seen_at is None:
            raise RuntimeError(
                "security signal window has no first event"
            )

        finding = SecurityFinding(
            security_rule_id=rule.id,
            runbook_id=rule.runbook_id,
            signal_key=signal.signal_key,
            severity=rule.severity,
            status=SecurityFindingStatus.OPEN.value,
            summary=signal.message,
            occurrence_count=occurrence_count,
            first_seen_at=first_seen_at,
            last_seen_at=signal.observed_at,
        )

        session.add(finding)
        changed_findings.append(finding)

    session.flush()

    return signal, changed_findings


def resolve_security_finding(
    session: Session,
    finding_id: UUID,
    resolved_at: datetime,
) -> SecurityFinding:
    finding = session.scalar(
        select(SecurityFinding)
        .where(
            SecurityFinding.id == finding_id
        )
        .with_for_update()
    )

    if finding is None:
        raise ValueError(
            f"security finding {finding_id} was not found"
        )

    if finding.status == SecurityFindingStatus.RESOLVED.value:
        return finding

    finding.status = SecurityFindingStatus.RESOLVED.value
    finding.resolved_at = resolved_at

    session.flush()

    return finding