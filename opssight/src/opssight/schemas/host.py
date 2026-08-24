from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class HostCreate(BaseModel):
    model_config = ConfigDict(
        str_strip_whitespace=True,
    )

    name: str = Field(
        min_length=1,
        max_length=200,
    )

    address: str = Field(
        min_length=1,
        max_length=255,
    )


class HostResponse(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,
    )

    id: UUID
    name: str
    address: str
    enabled: bool
    created_at: datetime