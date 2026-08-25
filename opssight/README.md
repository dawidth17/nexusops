# OpsSight

OpsSight is the monitoring, diagnostics, alerting, and security service of NexusOps.

It is written in Python using FastAPI and stores operational data in PostgreSQL with TimescaleDB. Background monitoring tasks are executed with Celery and RabbitMQ.

OpsSight currently provides:

- host and monitoring check management
- DNS, TCP, HTTP, and TLS checks
- scheduled background check execution
- telemetry storage with TimescaleDB
- deterministic alert evaluation
- security signal processing and findings
- runbook management
- role-based API authorization
- Prometheus metrics
- Docker-based local deployment
- automated tests and CI quality checks

## Tech stack

- Python 3.12
- FastAPI
- SQLAlchemy
- PostgreSQL
- TimescaleDB
- Alembic
- Celery
- RabbitMQ
- Prometheus
- Grafana
- Docker
- pytest
- Ruff
- mypy
- Bandit

## Structure

```text
opssight/
├── migrations/              database migrations
├── src/opssight/
│   ├── alerting/            alert evaluation
│   ├── api/                 REST API
│   ├── auth/                authorization
│   ├── checks/              DNS, TCP, HTTP, and TLS checks
│   ├── models/              SQLAlchemy models
│   ├── repositories/        database access
│   ├── schemas/             API schemas
│   ├── security/            security signal processing
│   ├── tasks/               Celery tasks and scheduling
│   ├── celery_app.py
│   ├── config.py
│   ├── database.py
│   ├── logging_config.py
│   ├── main.py
│   ├── messaging.py
│   └── metrics.py
├── tests/
├── Dockerfile
├── alembic.ini
├── pyproject.toml
└── uv.lock
```

## Architecture

OpsSight uses separate processes for the API, background workers, and scheduling.

```text
                         ┌──────────────┐
                         │   Clients    │
                         └──────┬───────┘
                                │
                                ▼
                         ┌──────────────┐
                         │ FastAPI API  │
                         └──────┬───────┘
                                │
                       ┌────────┴────────┐
                       │                 │
                       ▼                 ▼
              ┌────────────────┐   ┌──────────────┐
              │ TimescaleDB /  │   │   RabbitMQ   │
              │  PostgreSQL    │   └──────┬───────┘
              └────────────────┘          │
                                         ▼
                                  ┌──────────────┐
                                  │Celery worker │
                                  └──────┬───────┘
                                         │
                                         ▼
                                 monitoring checks

                           Celery scheduler
                                  │
                                  ▼
                         dispatch due checks
```

The same OpsSight Docker image is used for:

- API
- Celery worker
- Celery scheduler
- Alembic migrations

Each container runs a separate process.

## Monitoring checks

OpsSight supports four check types.

### DNS

Resolves a hostname and records whether DNS resolution succeeded.

### TCP

Attempts to establish a TCP connection to a target host and port.

### HTTP

Performs an HTTP request and evaluates whether the target responds successfully.

### TLS

Connects to a TLS endpoint and validates certificate information.

Check execution produces a common result containing information such as:

- success or failure
- execution duration
- message
- execution timestamp

This allows the alerting system to process different check types using the same result model.

## Alerting

Alert rules are associated with monitoring checks.

OpsSight tracks consecutive failures for a check and can open an alert after the configured failure threshold is reached.

When the check becomes healthy again, the open alert is recovered.

The basic lifecycle is:

```text
check succeeds
      │
      ▼
no alert

check fails
      │
      ▼
consecutive failures
      │
      ▼
threshold reached
      │
      ▼
alert opened
      │
      ▼
check succeeds
      │
      ▼
alert recovered
```

Alert evaluation is deterministic and stored in PostgreSQL.

## Security signals

OpsSight also contains a security signal processing pipeline.

Security signals contain:

- signal type
- signal key
- message
- timestamp
- optional host
- optional attributes

Security rules define a time window and occurrence threshold.

When enough matching signals occur inside the configured window, OpsSight creates a security finding.

Existing open findings are aggregated instead of creating a new finding for every signal.

Findings can later be resolved.

Runbooks can be associated with security rules and findings.

## Telemetry

Telemetry is stored in PostgreSQL using TimescaleDB.

The telemetry table is converted to a TimescaleDB hypertable, allowing time-based operational data to be queried efficiently.

Telemetry can be queried by time range through the repository layer.

## REST API

The REST API is versioned under:

```text
/api/v1
```

Current API resources include:

```text
/api/v1/hosts
/api/v1/checks
/api/v1/alerts
/api/v1/runbooks
/api/v1/security-findings
```

Operational endpoints:

```text
/health
/metrics
```

`/health` provides a basic service health check.

`/metrics` exposes Prometheus metrics.

## Authorization

OpsSight includes a role-based authorization abstraction.

Current roles include:

```text
viewer
operator
admin
```

Different API operations require different roles.

The current authorization layer is intentionally separated from the identity provider so that external authentication can be integrated later without coupling API business logic to a specific provider.

## Background processing

Celery is used for asynchronous monitoring work.

RabbitMQ is used as the Celery broker.

The main background components are:

```text
opssight-worker
opssight-scheduler
```

The scheduler finds monitoring checks that are due and publishes execution tasks.

Workers consume those tasks, execute the requested checks, update monitoring state, and evaluate alert rules.

## Observability

OpsSight exposes Prometheus metrics for both HTTP traffic and domain activity.

Examples include:

```text
opssight_http_requests_total
opssight_http_request_duration_seconds
opssight_check_executions_total
opssight_check_duration_seconds
opssight_alert_transitions_total
opssight_security_signals_total
opssight_security_finding_changes_total
opssight_scheduler_dispatched_checks_total
opssight_open_alerts
opssight_open_security_findings
opssight_state_metrics_collection_success
```

Event counters track actions performed by the running service.

Open alert and security finding gauges are collected from PostgreSQL so they represent persistent application state rather than only the state of the current process.

Prometheus scrapes the OpsSight API through the Docker network.

Grafana is provisioned with:

- a Prometheus datasource
- an OpsSight overview dashboard

## Local development

The project uses `uv` for Python dependency and environment management.

From **Ubuntu / WSL**:

```bash
cd ~/projects/nexusops/opssight

uv sync --locked --dev
```

Run the API locally:

```bash
set -a
source ../infra/compose/.env
set +a

uv run uvicorn \
  opssight.main:app \
  --reload
```

The API is available at:

```text
http://127.0.0.1:8000
```

Health check:

```bash
curl http://127.0.0.1:8000/health
```

Prometheus metrics:

```bash
curl http://127.0.0.1:8000/metrics
```

## Database migrations

Alembic manages the OpsSight database schema.

From **Ubuntu / WSL**:

```bash
cd ~/projects/nexusops/opssight

set -a
source ../infra/compose/.env
set +a

uv run alembic upgrade head
```

Show the current migration:

```bash
uv run alembic current
```

Check whether model changes require a migration:

```bash
uv run alembic check
```

## Docker Compose

The complete local infrastructure is managed from:

```text
infra/compose
```

It includes:

- ServiceCore PostgreSQL
- OpsSight TimescaleDB
- RabbitMQ
- OpsSight migration container
- OpsSight API
- OpsSight Celery worker
- OpsSight scheduler
- Prometheus
- Grafana

From **Ubuntu / WSL**:

```bash
cd ~/projects/nexusops/infra/compose

docker compose up -d --build
```

Check the services:

```bash
docker compose ps -a
```

The migration container should finish successfully and exit.

The API, worker, scheduler, database, RabbitMQ, Prometheus, and Grafana services remain running.

Useful local endpoints:

```text
OpsSight API       http://127.0.0.1:8000
Prometheus         http://127.0.0.1:9090
Grafana            http://127.0.0.1:3000
RabbitMQ UI        http://127.0.0.1:15672
```

Ports can be changed through the Compose environment file.

Stop the stack:

```bash
docker compose down
```

Stop the stack and remove its persistent volumes:

```bash
docker compose down -v
```

## Testing

Tests use pytest.

A separate test database should be used for database-backed tests.

From **Ubuntu / WSL**:

```bash
cd ~/projects/nexusops/opssight

set -a
source ../infra/compose/.env
set +a

OPSSIGHT_DB_NAME=opssight_test \
uv run pytest -v
```

The test infrastructure applies migrations and resets database state between tests while preserving the database schema.

## Quality checks

Run Ruff:

```bash
uv run ruff check src tests
```

Run mypy:

```bash
uv run mypy src
```

Run Bandit:

```bash
uv run bandit -q -r src
```

Build the Python package:

```bash
uv build
```

Build the Docker image:

```bash
cd ~/projects/nexusops

docker build \
  --tag nexusops-opssight:dev \
  opssight
```

The runtime container uses a dedicated non-root `opssight` user.

## CI

The OpsSight GitHub Actions workflow runs automatically for relevant changes.

Current quality gates include:

```text
Ruff
mypy
Bandit
Alembic migrations
pytest
Python package build
Docker Compose validation
Prometheus configuration validation
Grafana dashboard JSON validation
Docker image build
non-root container verification
container smoke test
Prometheus state metrics verification
Trivy image scan
```

The Docker smoke test starts a real TimescaleDB container, applies the OpsSight migrations, starts the OpsSight image, and verifies the health and metrics endpoints.

## Current scope

OpsSight currently provides the monitoring and security processing foundation for NexusOps.

The service is designed so that additional NexusOps components can send telemetry and operational data to it through versioned integration contracts in later project milestones.