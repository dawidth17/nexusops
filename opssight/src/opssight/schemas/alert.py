from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict


class AlertResponse(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,
    )

    id: UUID
    alert_rule_id: UUID
    status: str
    correlation_id: str
    message: str
    opened_at: datetime
    recovered_at: datetime | None
