import re
from datetime import UTC, datetime
from hashlib import sha256
from uuid import UUID

_CORRELATION_ID_PATTERN = re.compile(
    r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
)


def validate_correlation_id(
    value: str,
) -> str:
    if _CORRELATION_ID_PATTERN.fullmatch(
        value
    ) is None:
        raise ValueError(
            "correlation_id must contain between 1 and 128 "
            "characters and use only letters, digits, '.', '_', "
            "':' or '-'"
        )

    return value


def deterministic_correlation_id(
    *parts: str,
) -> str:
    material = "|".join(
        parts
    ).encode(
        "utf-8"
    )

    return sha256(
        material
    ).hexdigest()


def resolve_telemetry_correlation_id(
    agent_id: str,
    batch_id: str,
    incoming_correlation_id: str | None,
) -> str:
    if (
        incoming_correlation_id is not None
        and incoming_correlation_id != ""
    ):
        return validate_correlation_id(
            incoming_correlation_id
        )

    return deterministic_correlation_id(
        "sentinel-agent",
        agent_id,
        batch_id,
    )


def resolve_check_correlation_id(
    check_id: UUID,
    started_at: datetime,
    incoming_correlation_id: str | None,
) -> str:
    if (
        incoming_correlation_id is not None
        and incoming_correlation_id != ""
    ):
        return validate_correlation_id(
            incoming_correlation_id
        )

    if (
        started_at.tzinfo is None
        or started_at.utcoffset() is None
    ):
        raise ValueError(
            "check started_at must be timezone-aware"
        )

    started_at_utc = (
        started_at
        .astimezone(UTC)
        .isoformat(
            timespec="microseconds"
        )
        .replace(
            "+00:00",
            "Z",
        )
    )

    return deterministic_correlation_id(
        "opssight-check",
        str(check_id),
        started_at_utc,
    )
