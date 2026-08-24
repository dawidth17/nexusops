from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class RunbookCreate(BaseModel):
    model_config = ConfigDict(
        str_strip_whitespace=True,
    )

    name: str = Field(
        min_length=1,
        max_length=200,
    )

    description: str = Field(
        min_length=1,
    )

    instructions: str = Field(
        min_length=1,
    )


class RunbookResponse(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,
    )

    id: UUID
    name: str
    description: str
    instructions: str
    created_at: datetime