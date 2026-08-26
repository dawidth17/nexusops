import json
import logging
import sys
from datetime import UTC, datetime


class JsonFormatter(logging.Formatter):
    def format(
        self,
        record: logging.LogRecord,
    ) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }

        structured_fields = (
            "service",
            "environment",
            "correlation_id",
            "event_id",
            "event_type",
            "alert_id",
            "agent_id",
            "batch_id",
            "check_id",
            "attempt",
            "error",
        )

        for field_name in structured_fields:
            value = getattr(
                record,
                field_name,
                None,
            )

            if value is not None:
                payload[field_name] = value

        if record.exc_info is not None:
            payload["exception"] = self.formatException(
                record.exc_info
            )

        return json.dumps(
            payload
        )


def configure_logging(
    log_level: str,
) -> None:
    handler = logging.StreamHandler(
        sys.stdout
    )

    handler.setFormatter(
        JsonFormatter()
    )

    root_logger = logging.getLogger()

    root_logger.handlers.clear()

    root_logger.setLevel(
        log_level.upper()
    )

    root_logger.addHandler(
        handler
    )
