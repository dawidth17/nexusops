from dataclasses import dataclass
from enum import IntEnum


class Role(IntEnum):
    VIEWER = 10
    OPERATOR = 20
    ADMIN = 30


@dataclass(frozen=True)
class Principal:
    subject: str
    role: Role