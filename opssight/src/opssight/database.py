from sqlalchemy import URL, create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import sessionmaker

from opssight.config import settings


def build_database_url() -> URL:
    return URL.create(
        drivername="postgresql+psycopg",
        username=settings.db_user,
        password=settings.db_password.get_secret_value(),
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
    )


engine: Engine = create_engine(
    build_database_url(),
    pool_pre_ping=True,
)


SessionFactory = sessionmaker(
    bind=engine,
    autoflush=False,
    expire_on_commit=False,
)