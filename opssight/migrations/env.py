from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, pool

from opssight.database import build_database_url
from opssight.models import (
    Agent,
    Alert,
    AlertRule,
    Check,
    Host,
    OutboxEvent,
    Runbook,
    SecurityFinding,
    SecurityRule,
    SecuritySignal,
    Telemetry,
    TelemetryBatch,
)
from opssight.models.base import Base


config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata

_ = (
    Agent,
    Alert,
    AlertRule,
    Check,
    Host,
    OutboxEvent,
    Runbook,
    SecurityFinding,
    SecurityRule,
    SecuritySignal,
    Telemetry,
    TelemetryBatch,
)


def include_object(
    _object: object,
    name: str | None,
    type_: str,
    reflected: bool,
    compare_to: object | None,
) -> bool:
    if (
        type_ == "index"
        and reflected
        and compare_to is None
        and name == "telemetry_captured_at_idx"
    ):
        return False

    return True


def run_migrations_offline() -> None:
    database_url = build_database_url().render_as_string(
        hide_password=False,
    )

    context.configure(
        url=database_url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        include_object=include_object,
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
            include_object=include_object,
        )

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()