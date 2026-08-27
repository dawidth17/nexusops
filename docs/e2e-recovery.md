# NexusOps end-to-end recovery scenario

The INT-410 scenario proves the integrated NexusOps v1.0 monitoring path with real services and real transport boundaries.

## Flow

1. An isolated Docker Compose project starts ServiceCore, OpsSight, TimescaleDB, RabbitMQ, Kafka, Keycloak and the Console.
2. Ephemeral development certificates are generated for the OpsSight gRPC server and the `e2e-agent` identity.
3. The real C++ SentinelAgent connects to the real OpsSight gRPC server with Protobuf over mTLS.
4. SentinelAgent sends a heartbeat and real system, network and process telemetry.
5. OpsSight persists the telemetry in TimescaleDB and records the telemetry correlation ID.
6. The scenario creates a TCP check attached to the host registered by SentinelAgent.
7. The recovery target is intentionally absent, so the real OpsSight check executor produces a deterministic failure using the same correlation ID.
8. OpsSight opens one alert and creates `nexusops.alert.opened` in the transactional outbox.
9. The real outbox publisher sends the event to Kafka.
10. ServiceCore consumes the event and creates exactly one `MONITORING` incident.
11. The same Kafka event is deliberately delivered again and ServiceCore proves idempotency by keeping exactly one incident.
12. The recovery target starts and the same TCP check succeeds.
13. OpsSight recovers the existing alert and publishes `nexusops.alert.recovered` through the outbox and Kafka.
14. ServiceCore records recovery context on the existing incident without creating a new incident.
15. Playwright signs in through the real Keycloak flow and verifies the host, recovered alert, linked incident and shared correlation ID in the real Console.

The active TCP check is executed by OpsSight. SentinelAgent establishes the real host identity and telemetry path used by the scenario, while the correlation ID from the accepted telemetry batch is deliberately propagated into the check execution so the integration can be traced end to end.

## Local prerequisites

The scenario expects:

- Docker Engine or Docker Desktop with Docker Compose
- Node.js 22 and npm
- OpenSSL
- the Console dependencies installed with `npm ci`
- Playwright Chromium installed with `npx playwright install chromium`

The normal `nexusops-dev` stack must be stopped because the E2E stack intentionally uses the normal local NexusOps ports required by the Keycloak Console redirect configuration. Stop the development stack without deleting its volumes:

```bash
cd infra/compose
docker compose stop
```

## Run

From the repository root:

```bash
chmod +x e2e/run-recovery.sh
chmod +x infra/certs/dev-mtls.sh

e2e/run-recovery.sh
```

By default the runner uses `e2e/.env.example`. Create an ignored `e2e/.env` if local overrides are required.

The runner uses the isolated Compose project name `nexusops-e2e`. It does not delete the `nexusops-dev` database volumes. E2E containers and E2E volumes are removed after the run.

To preserve the E2E stack after a run for manual inspection:

```bash
NEXUSOPS_E2E_KEEP_STACK=1 e2e/run-recovery.sh
```

When preserved, the Console is available at:

```text
http://127.0.0.1:3001
```

The default E2E Console credentials are defined in `e2e/.env.example`.

## Success criteria

A successful run prints `NexusOps E2E recovery scenario passed` together with the host, correlation, alert, incident and outbox event identifiers.

The runner verifies:

- real mTLS SentinelAgent authentication
- persisted telemetry
- shared correlation identity
- deterministic alert opening
- transactional outbox creation and publication
- Kafka delivery
- exactly one ServiceCore monitoring incident
- duplicate Kafka idempotency
- alert recovery
- recovery context on the same incident
- real authenticated Console visibility

## CI

`.github/workflows/e2e-ci.yml` installs the Console dependencies and Playwright Chromium, then runs the same `e2e/run-recovery.sh` used locally. This keeps the developer demonstration and CI scenario aligned.