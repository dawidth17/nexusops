# OpsSight

OpsSight is the monitoring, diagnostics, alerting, telemetry, and security service of NexusOps.

It is written in Python using FastAPI and stores operational data in PostgreSQL with TimescaleDB. Background monitoring tasks are executed with Celery and RabbitMQ.

OpsSight currently provides:

- host and monitoring check management
- DNS, TCP, HTTP, and TLS checks
- scheduled background check execution
- telemetry ingestion through gRPC
- mutual TLS authentication for SentinelAgent
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
- gRPC
- Protobuf
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
│   ├── grpc/                telemetry gRPC server and security
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

Generated Protobuf Python bindings are created from the shared NexusOps contracts and are not stored in Git.

## Architecture

OpsSight uses separate processes for the API, background workers, and scheduling.

The FastAPI process also starts the OpsSight telemetry gRPC server.

```text
                    SentinelAgent
                         │
                         │ telemetry.v1
                         │ gRPC + mTLS
                         ▼
                  ┌───────────────┐
                  │   OpsSight    │
                  │ gRPC server   │
                  └───────┬───────┘
                          │
                          ▼
                  ┌───────────────┐
                  │ TimescaleDB / │
                  │  PostgreSQL   │
                  └───────────────┘


                         Clients
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

SentinelAgent sends telemetry to OpsSight using the shared versioned Protobuf contract:

```text
nexusops.telemetry.v1
```

The transport uses a streaming gRPC service.

Each SentinelAgent sends a heartbeat containing its agent identity, hostname, and version before telemetry delivery.

Collected metric records are sent in deterministic batches.

OpsSight acknowledges accepted batches with the corresponding batch ID and acknowledged sequence.

Repeated batches are detected so retransmission after a transport failure does not create duplicate telemetry.

Telemetry is stored in PostgreSQL using TimescaleDB.

The telemetry table is converted to a TimescaleDB hypertable, allowing time-based operational data to be queried efficiently.

Telemetry can be queried by time range through the repository layer.

## SentinelAgent mTLS

The SentinelAgent to OpsSight gRPC connection supports mutual TLS.

When gRPC mTLS is enabled:

- OpsSight presents a server certificate
- SentinelAgent validates the server certificate against the configured CA
- SentinelAgent presents its own client certificate
- OpsSight validates the client certificate against the configured CA
- OpsSight extracts the client certificate common name as the authenticated agent identity
- the authenticated identity must exist in the configured agent allowlist
- the telemetry payload `agent_id` must match the authenticated certificate identity

This prevents an agent from impersonating another enrolled agent by only changing the `agent_id` inside a telemetry payload.

OpsSight does not downgrade to an insecure gRPC listener when mTLS is enabled but its security configuration is incomplete.

The main OpsSight settings are:

```text
OPSSIGHT_GRPC_MTLS_ENABLED
OPSSIGHT_GRPC_MTLS_CA_CERTIFICATE_PATH
OPSSIGHT_GRPC_MTLS_SERVER_CERTIFICATE_PATH
OPSSIGHT_GRPC_MTLS_SERVER_PRIVATE_KEY_PATH
OPSSIGHT_GRPC_MTLS_ALLOWED_AGENT_IDS
```

For example:

```bash
export OPSSIGHT_GRPC_MTLS_ENABLED=true

export OPSSIGHT_GRPC_MTLS_CA_CERTIFICATE_PATH=\
../infra/certs/generated/ca/ca.cert.pem

export OPSSIGHT_GRPC_MTLS_SERVER_CERTIFICATE_PATH=\
../infra/certs/generated/server/server.cert.pem

export OPSSIGHT_GRPC_MTLS_SERVER_PRIVATE_KEY_PATH=\
../infra/certs/generated/server/server.key.pem

export OPSSIGHT_GRPC_MTLS_ALLOWED_AGENT_IDS=\
sentinel-local
```

Development certificate generation, agent enrollment, certificate rotation, and the complete local mTLS integration procedure are documented in:

```text
docs/security/mtls.md
```

Generated certificates and private keys are not committed to Git.

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

Generate the Protobuf bindings:

```bash
uv run python scripts/generate_protobuf.py \
  --contract-root ../contracts/protobuf \
  --output-root src
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

The default gRPC endpoint is:

```text
127.0.0.1:50051
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

The gRPC integration tests also cover:

- successful authenticated mTLS telemetry
- certificate identity to payload identity binding
- rejection of unknown but CA-signed agent identities
- rejection of certificates signed by an unknown CA

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
uv run bandit \
  -q \
  -r src \
  -x src/opssight/generated
```

Build the Python package:

```bash
uv build
```

Build the Docker image from the NexusOps repository root:

```bash
cd ~/projects/nexusops

docker build \
  --file opssight/Dockerfile \
  --tag nexusops-opssight:dev \
  .
```

The runtime container uses a dedicated non-root `opssight` user.

The image exposes the HTTP API port and the gRPC telemetry port:

```text
8000
50051
```

## CI

The OpsSight GitHub Actions workflow runs automatically for relevant changes.

Current quality gates include:

```text
Protobuf generation and import validation
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

OpsSight currently provides the monitoring, telemetry, alerting, and security processing foundation for NexusOps.

SentinelAgent can deliver durable Protobuf telemetry to OpsSight through the shared `telemetry.v1` gRPC contract, with mutual TLS providing transport encryption and certificate-bound agent identity.