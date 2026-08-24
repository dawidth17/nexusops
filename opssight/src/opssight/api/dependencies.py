from collections.abc import Generator

from sqlalchemy.orm import Session

from opssight.database import SessionFactory


def get_session() -> Generator[Session, None, None]:
    with SessionFactory() as session:
        yield session