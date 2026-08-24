from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict


class SecurityFindingResponse(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,
    )

    id: UUID
    security_rule_id: UUID
    runbook_id: UUID
    signal_key: str
    severity: str
    status: str
    summary: str
    occurrence_count: int
    first_seen_at: datetime
    last_seen_at: datetime
    resolved_at: datetime | None