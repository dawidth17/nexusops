FROM python:3.12-slim-trixie AS builder

COPY --from=ghcr.io/astral-sh/uv:0.12.5 /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=0

WORKDIR /app

COPY opssight/pyproject.toml \
    opssight/uv.lock \
    opssight/README.md \
    ./

RUN uv sync \
    --locked \
    --dev \
    --no-install-project

COPY opssight/src ./src
COPY opssight/scripts ./scripts
COPY opssight/migrations ./migrations
COPY opssight/alembic.ini ./

COPY contracts/protobuf /contracts/protobuf

RUN uv run python \
    scripts/generate_protobuf.py \
    --contract-root /contracts/protobuf \
    --output-root /app/src

RUN uv sync \
    --locked \
    --no-dev \
    --no-editable


FROM python:3.12-slim-trixie AS runtime

ENV PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"

WORKDIR /app

RUN groupadd \
      --system \
      opssight \
    && useradd \
      --system \
      --gid opssight \
      --home-dir /app \
      --no-create-home \
      opssight

COPY --from=builder \
    --chown=opssight:opssight \
    /app/.venv \
    /app/.venv

COPY --from=builder \
    --chown=opssight:opssight \
    /app/alembic.ini \
    ./

COPY --from=builder \
    --chown=opssight:opssight \
    /app/migrations \
    ./migrations

USER opssight

EXPOSE 8000

CMD ["uvicorn", "opssight.main:app", "--host", "0.0.0.0", "--port", "8000"]