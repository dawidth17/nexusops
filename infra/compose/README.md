# Local infrastructure

The local infrastructure is managed with Docker Compose.

It includes:

- PostgreSQL for ServiceCore
- TimescaleDB for OpsSight
- RabbitMQ for OpsSight background tasks
- Prometheus for metrics collection
- Grafana for dashboards

## 1. Create the local environment file

From `infra/compose`, copy the example configuration:

```bash
cp .env.example .env
```

The `.env` file is local and is not committed to Git.

Example:

```env
SERVICECORE_DB_NAME=servicecore
SERVICECORE_DB_USER=servicecore
SERVICECORE_DB_PASSWORD=change_me
SERVICECORE_DB_PORT=5433

OPSSIGHT_DB_NAME=opssight
OPSSIGHT_DB_USER=opssight
OPSSIGHT_DB_PASSWORD=change_me
OPSSIGHT_DB_PORT=5434

OPSSIGHT_RABBITMQ_USER=opssight
OPSSIGHT_RABBITMQ_PASSWORD=change_me
OPSSIGHT_RABBITMQ_PORT=5672
OPSSIGHT_RABBITMQ_MANAGEMENT_PORT=15672

OPSSIGHT_HOST_IP=

PROMETHEUS_PORT=9090

GRAFANA_PORT=3000
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change_me
```

Change the local passwords before starting the services.

## 2. WSL address for OpsSight metrics

During local development, OpsSight runs directly in WSL while Prometheus runs in Docker.

Find the current WSL address:

```bash
hostname -I
```

Use the first address as `OPSSIGHT_HOST_IP` in `.env`.

For example:

```env
OPSSIGHT_HOST_IP=172.23.239.157
```

The WSL address can change after restarting Windows or WSL, so update this value if Prometheus can no longer reach OpsSight.

When running OpsSight for Prometheus scraping, start Uvicorn with:

```bash
uv run uvicorn opssight.main:app --host 0.0.0.0 --port 8000 --reload
```

## 3. Validate the Docker Compose configuration

From `infra/compose`:

```bash
docker compose config --quiet
```

If no error is displayed, the configuration is valid.

List the configured services:

```bash
docker compose config --services
```

Expected services:

```text
servicecore-db
opssight-db
rabbitmq
prometheus
grafana
```

Validate the Prometheus configuration:

```bash
docker compose run --rm \
  --entrypoint promtool \
  prometheus \
  check config \
  /etc/prometheus/prometheus.yml
```

## 4. Start the infrastructure

Start all services:

```bash
docker compose up -d
```

Check their status:

```bash
docker compose ps
```

Individual services can also be started separately:

```bash
docker compose up -d servicecore-db
docker compose up -d opssight-db
docker compose up -d rabbitmq
docker compose up -d prometheus
docker compose up -d grafana
```

## 5. ServiceCore PostgreSQL

ServiceCore uses PostgreSQL.

The default local port mapping is:

```text
127.0.0.1:5433 -> 5432
```

Port `5433` is used locally because port `5432` may already be used by another PostgreSQL instance.

Connect to the database:

```bash
docker compose exec servicecore-db \
  psql -U servicecore -d servicecore
```

Useful checks:

```sql
SELECT version();
SELECT current_database();
SELECT current_user;
```

Exit with:

```text
\q
```

## 6. OpsSight TimescaleDB

OpsSight uses PostgreSQL with the TimescaleDB extension for telemetry storage.

The default local port mapping is:

```text
127.0.0.1:5434 -> 5432
```

Connect to the database:

```bash
docker compose exec opssight-db \
  psql -U opssight -d opssight
```

Check the TimescaleDB extension:

```sql
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'timescaledb';
```

Exit with:

```text
\q
```

## 7. RabbitMQ

RabbitMQ is used by OpsSight for Celery tasks.

Default local ports:

```text
5672  - AMQP
15672 - management UI
```

Check RabbitMQ:

```bash
docker compose exec rabbitmq \
  rabbitmq-diagnostics -q ping
```

The management UI is available at:

```text
http://127.0.0.1:15672
```

Use the credentials configured in `.env`.

## 8. Prometheus

Prometheus collects metrics exposed by OpsSight at:

```text
http://127.0.0.1:8000/metrics
```

Prometheus itself is available at:

```text
http://127.0.0.1:9090
```

Check readiness:

```bash
curl -s http://127.0.0.1:9090/-/ready
```

Check the configured targets:

```bash
curl -s \
  http://127.0.0.1:9090/api/v1/targets \
  | python3 -m json.tool
```

The OpsSight target should report:

```text
health: up
```

A simple PromQL check is:

```bash
curl -sG \
  http://127.0.0.1:9090/api/v1/query \
  --data-urlencode 'query=up{job="opssight"}' \
  | python3 -m json.tool
```

A value of `1` means Prometheus can successfully scrape OpsSight.

Other useful metrics include:

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

## 9. Grafana

Grafana uses Prometheus as its provisioned datasource.

Grafana is available at:

```text
http://127.0.0.1:3000
```

Use the admin credentials configured in `.env`.

Check Grafana:

```bash
curl -s \
  http://127.0.0.1:3000/api/health \
  | python3 -m json.tool
```

The Prometheus datasource and OpsSight dashboard are provisioned automatically from files stored in the repository.

The dashboard can be found under:

```text
Dashboards
NexusOps
OpsSight Overview
```

The dashboard includes:

- OpsSight availability
- state metrics collection status
- open monitoring alerts
- open security findings
- HTTP request rate
- check execution rate
- check duration
- alert transitions
- security signal rate
- scheduler dispatch rate

Some graphs may show no data until OpsSight generates the corresponding events.

## 10. Logs

Show logs for all infrastructure services:

```bash
docker compose logs
```

Follow logs:

```bash
docker compose logs -f
```

Show logs for one service:

```bash
docker compose logs prometheus
docker compose logs grafana
docker compose logs rabbitmq
docker compose logs opssight-db
docker compose logs servicecore-db
```

## 11. Stop the infrastructure

Stop and remove the containers:

```bash
docker compose down
```

Named volumes are preserved, so database and monitoring data remain available when the services are started again.

To also delete the named volumes:

```bash
docker compose down -v
```

Use `-v` only when the stored local data can be discarded.