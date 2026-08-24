from enum import StrEnum


class AlertSeverity(StrEnum):
    INFO = "info"
    WARNING = "warning"
    CRITICAL = "critical"


class AlertStatus(StrEnum):
    OPEN = "open"
    RECOVERED = "recovered"


class SecurityFindingStatus(StrEnum):
    OPEN = "open"
    RESOLVED = "resolved"