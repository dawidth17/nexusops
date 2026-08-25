from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

CheckType = Literal[
    "dns",
    "tcp",
    "http",
    "https",
    "tls",
]


class CheckCreate(BaseModel):
    model_config = ConfigDict(
        str_strip_whitespace=True,
    )

    host_id: UUID

    name: str = Field(
        min_length=1,
        max_length=200,
    )

    check_type: CheckType

    target: str = Field(
        min_length=1,
        max_length=500,
    )

    interval_seconds: int = Field(
        default=60,
        gt=0,
    )

    timeout_seconds: int = Field(
        default=5,
        gt=0,
    )


class CheckResponse(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,
    )

    id: UUID
    host_id: UUID
    name: str
    check_type: str
    target: str
    interval_seconds: int
    timeout_seconds: int
    consecutive_failures: int
    next_run_at: datetime
    enabled: bool
    created_at: datetime