from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True)
class CheckResult:
    success: bool
    started_at: datetime
    duration_ms: float
    message: str
    correlation_id: str | None = None
