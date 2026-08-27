# NexusOps

> **AI-assisted learning project**
>
> NexusOps was developed extensively with the assistance of AI as a learning and experimentation project. The goal was to simulate the architecture, development workflow and engineering practices of a larger software project similar to one that could be developed within a professional engineering team.
>
> I used the project to become familiar with multiple technologies, programming languages, infrastructure components, security concepts and integration patterns while learning how they interact in a complete system. AI was used throughout the project for architecture discussions, implementation guidance, code generation, debugging, testing and documentation.
>
> This repository should therefore not be interpreted as a claim that I independently possess expert-level knowledge of every technology or implementation contained in the project. Instead, it represents a practical environment in which I studied, reviewed, tested and integrated those technologies while gradually improving my understanding of the system.
>
> A secondary goal of NexusOps was to explore how effectively modern AI tools can assist in designing and building a relatively large, multi-component software engineering project while maintaining consistent architecture, testing, security practices and documentation.

NexusOps is an integrated IT operations platform built as a personal software engineering project across multiple languages and infrastructure technologies.

The v1.0 integration connects a Linux monitoring agent, a monitoring service, an incident-management service and a shared operator Console through versioned contracts and authenticated service boundaries.

## Architecture

```text
SentinelAgent (C/C++)
        |
        | Protobuf + gRPC + mTLS
        v
OpsSight (Python / FastAPI)
        |
        | TimescaleDB
        |
        | transactional outbox
        v
      Kafka
        |
        v
ServiceCore (Java / Spring Boot)
        |
        | PostgreSQL
        v
NexusOps Console (React / TypeScript)

Keycloak provides shared OIDC authentication and role-based access.
Prometheus and Grafana provide platform observability.
RabbitMQ and Celery execute OpsSight background work.
```

## Components

- `sentinel-agent/` - Linux telemetry collection, durable SQLite spool and gRPC transport
- `opssight/` - telemetry ingestion, active checks, alerting, outbox publishing and monitoring APIs
- `servicecore/` - incident lifecycle management and monitoring-event integration
- `console/` - authenticated React/TypeScript operator interface
- `contracts/` - shared Protobuf and Kafka event contracts
- `infra/` - Docker Compose, Keycloak, Kafka, certificates, Prometheus and Grafana configuration
- `e2e/` - reproducible full-system recovery scenario

## Main technologies

- Java 21 and Spring Boot
- Python 3.12 and FastAPI
- C17 and C++20
- React and TypeScript
- PostgreSQL and TimescaleDB
- Kafka
- RabbitMQ and Celery
- gRPC and Protobuf
- mTLS and OpenID Connect
- Docker Compose
- Prometheus and Grafana
- GitHub Actions
- Playwright, Vitest, pytest and JUnit

## Integrated monitoring flow

A real SentinelAgent authenticates to OpsSight with mTLS and sends versioned Protobuf telemetry. OpsSight persists telemetry, executes monitoring checks and manages alert lifecycle state. Alert lifecycle events are written to a transactional outbox and published to Kafka. ServiceCore consumes those events idempotently, creates monitoring incidents and records later recovery context on the same incident. The Console exposes the linked host, alert and incident state to authenticated operators.

Correlation IDs are preserved across the integration path so related telemetry, alerts, events and incidents can be traced together.

## Local development

The local stack is defined in `infra/compose/compose.yml`.

```bash
cd infra/compose
cp .env.example .env
docker compose up -d --build
```

The main local endpoints are:

- Console: `http://127.0.0.1:3001`
- Keycloak: `http://127.0.0.1:8081`
- ServiceCore: `http://127.0.0.1:8080`
- OpsSight: `http://127.0.0.1:8000`
- Grafana: `http://127.0.0.1:3000`
- Prometheus: `http://127.0.0.1:9090`

Development credentials and local ports are configured through `infra/compose/.env` from the committed `.env.example` template.

## Full end-to-end recovery scenario

INT-410 provides a reproducible scenario that proves:

```text
SentinelAgent
    -> OpsSight telemetry persistence
    -> deterministic monitoring failure
    -> AlertOpened
    -> transactional outbox
    -> Kafka
    -> one ServiceCore monitoring incident
    -> duplicate event remains idempotent
    -> monitoring recovery
    -> AlertRecovered
    -> same ServiceCore incident updated
    -> authenticated Console verification
```

See [`docs/e2e-recovery.md`](docs/e2e-recovery.md) for prerequisites, execution and verification details.

Run it from the repository root with:

```bash
e2e/run-recovery.sh
```

## Status

NexusOps v1.0 integrated baseline is implemented. Component development continues on top of the end-to-end monitored recovery workflow.