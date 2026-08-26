from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator
from jsonschema import FormatChecker
from jsonschema.exceptions import ValidationError
from referencing import Registry
from referencing import Resource


EVENTS_ROOT = Path(__file__).resolve().parents[1]
VERSION_ROOT = EVENTS_ROOT / "v1"
EXAMPLES_ROOT = VERSION_ROOT / "examples"

SCHEMA_FILES = (
    "event-envelope.schema.json",
    "alert-lifecycle.schema.json",
    "incident-lifecycle.schema.json",
)

EXAMPLE_SCHEMAS = {
    "alert-opened.json": "alert-lifecycle.schema.json",
    "alert-recovered.json": "alert-lifecycle.schema.json",
    "incident-created.json": "incident-lifecycle.schema.json",
    "incident-resolved.json": "incident-lifecycle.schema.json",
}


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as file:
        document = json.load(file)

    if not isinstance(document, dict):
        raise TypeError(f"{path} must contain a JSON object")

    return document


def build_registry(
    schemas: dict[str, dict[str, Any]],
) -> Registry:
    registry = Registry()

    for schema in schemas.values():
        Draft202012Validator.check_schema(schema)

        schema_id = schema.get("$id")

        if not isinstance(schema_id, str) or not schema_id:
            raise ValueError("every event schema must define $id")

        registry = registry.with_resource(
            schema_id,
            Resource.from_contents(schema),
        )

    return registry


def build_validator(
    schema: dict[str, Any],
    registry: Registry,
) -> Draft202012Validator:
    return Draft202012Validator(
        schema,
        registry=registry,
        format_checker=FormatChecker(),
    )


def assert_invalid(
    validator: Draft202012Validator,
    document: dict[str, Any],
    name: str,
) -> None:
    try:
        validator.validate(document)
    except ValidationError:
        return

    raise AssertionError(
        f"{name} unexpectedly passed validation"
    )


def main() -> None:
    schemas = {
        name: load_json(VERSION_ROOT / name)
        for name in SCHEMA_FILES
    }

    registry = build_registry(schemas)

    validators = {
        name: build_validator(schema, registry)
        for name, schema in schemas.items()
    }

    for example_name, schema_name in EXAMPLE_SCHEMAS.items():
        example = load_json(
            EXAMPLES_ROOT / example_name
        )

        validators[schema_name].validate(example)

        print(
            f"validated {example_name} "
            f"against {schema_name}"
        )

    alert_opened = load_json(
        EXAMPLES_ROOT / "alert-opened.json"
    )

    missing_event_id = deepcopy(alert_opened)
    missing_event_id.pop("eventId")

    assert_invalid(
        validators["alert-lifecycle.schema.json"],
        missing_event_id,
        "event without eventId",
    )

    missing_correlation_id = deepcopy(alert_opened)
    missing_correlation_id.pop("correlationId")

    assert_invalid(
        validators["alert-lifecycle.schema.json"],
        missing_correlation_id,
        "event without correlationId",
    )

    non_utc_timestamp = deepcopy(alert_opened)
    non_utc_timestamp["occurredAt"] = (
        "2026-08-26T13:00:00+03:00"
    )

    assert_invalid(
        validators["alert-lifecycle.schema.json"],
        non_utc_timestamp,
        "event with non-UTC occurredAt",
    )

    invalid_alert_state = deepcopy(alert_opened)
    invalid_alert_state["payload"]["status"] = (
        "recovered"
    )

    assert_invalid(
        validators["alert-lifecycle.schema.json"],
        invalid_alert_state,
        "AlertOpened with recovered status",
    )

    print("event schemas and examples are valid")


if __name__ == "__main__":
    main()