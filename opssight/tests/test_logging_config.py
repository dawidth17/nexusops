import json
import logging

from opssight.logging_config import JsonFormatter


def test_json_formatter_includes_correlation_id() -> None:
    record = logging.LogRecord(
        name="opssight.test",
        level=logging.INFO,
        pathname=__file__,
        lineno=10,
        msg="event processed",
        args=(),
        exc_info=None,
    )

    record.correlation_id = (
        "test-correlation-id"
    )

    record.event_id = (
        "test-event-id"
    )

    formatter = JsonFormatter()

    payload = json.loads(
        formatter.format(
            record
        )
    )

    assert (
        payload["message"]
        == "event processed"
    )

    assert (
        payload["correlation_id"]
        == "test-correlation-id"
    )

    assert (
        payload["event_id"]
        == "test-event-id"
    )
