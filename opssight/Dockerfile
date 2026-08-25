FROM python:3.12-slim-trixie AS builder

COPY --from=ghcr.io/astral-sh/uv:0.12.5 /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=0

WORKDIR /app

COPY pyproject.toml uv.lock README.md ./

RUN uv sync \
    --locked \
    --no-dev \
    --no-install-project

COPY src ./src
COPY migrations ./migrations
COPY alembic.ini ./

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

COPY --chown=opssight:opssight \
    alembic.ini \
    ./

COPY --chown=opssight:opssight \
    migrations \
    ./migrations

USER opssight

EXPOSE 8000

CMD ["uvicorn", "opssight.main:app", "--host", "0.0.0.0", "--port", "8000"]