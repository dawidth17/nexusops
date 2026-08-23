import json
import logging
import sys
from datetime import UTC, datetime

class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        
        service = getattr(record, "service", None)
        environment = getattr(record, "environment", None)
        
        if service is not None:
            payload["service"] = service
            
        if environment is not None:
            payload["environment"] = environment
            
        if record.exc_info is not None:
            payload["exception"] = self.formatException(record.exc_info)
            
        return json.dumps(payload)
    
def configure_logging(log_level: str) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    
    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.setLevel(log_level.upper())
    root_logger.addHandler(handler)
    