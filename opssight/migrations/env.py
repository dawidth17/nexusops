from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, pool

from opssight.database import build_database_url
from opssight.models import Alert, AlertRule, Check, Host
from opssight.models.base import Base


config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata

_ = (
    Alert,
    AlertRule,
    Check,
    Host,
)


def run_migrations_offline() -> None:
    database_url = build_database_url().render_as_string(
        hide_password=False,
    )

    context.configure(
        url=database_url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = create_engine(
        build_database_url(),
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
        )

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()