#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
base_compose="$repo_root/infra/compose/compose.yml"
overlay_compose="$script_dir/compose.e2e.yml"
env_file="${NEXUSOPS_E2E_ENV_FILE:-$script_dir/.env}"
project_name="${NEXUSOPS_E2E_PROJECT_NAME:-nexusops-e2e}"
cert_root="$repo_root/infra/certs/generated/e2e"
agent_id="e2e-agent"
host_name="e2e-host"
check_name="e2e-recovery-check"
rule_name="e2e-recovery-rule"
target="e2e-target:18080"
keep_stack="${NEXUSOPS_E2E_KEEP_STACK:-0}"

if [[ ! -f "$env_file" ]]; then
    env_file="$script_dir/.env.example"
fi

require_command()
{
    command -v "$1" >/dev/null 2>&1 || {
        printf 'error: required command not found: %s\n' "$1" >&2
        exit 1
    }
}

require_command docker
require_command python3
require_command curl
require_command npm

if [[ ! -x "$repo_root/console/node_modules/.bin/playwright" ]]; then
    cat >&2 <<'MESSAGE'
error: Console dependencies are not installed.
run this first:

  cd console
  npm ci
  npx playwright install chromium
MESSAGE
    exit 1
fi

set -a
source "$env_file"
set +a

export NEXUSOPS_REPO_ROOT="$repo_root"
export NEXUSOPS_E2E_CERT_ROOT="$cert_root"

compose=(
    docker compose
    --env-file "$env_file"
    -p "$project_name"
    -f "$base_compose"
    -f "$overlay_compose"
)

cleanup()
{
    local exit_code=$?

    if [[ "$keep_stack" == "1" ]]; then
        printf 'e2e stack preserved: project=%s\n' "$project_name"
        exit "$exit_code"
    fi

    if (( exit_code != 0 )); then
        printf '\nE2E failure diagnostics:\n' >&2
        "${compose[@]}" ps -a >&2 || true
        "${compose[@]}" logs --no-color --tail=120 >&2 || true
    fi

    "${compose[@]}" down --remove-orphans >/dev/null 2>&1 || true

    mapfile -t project_containers < <(
        docker ps -aq \
            --filter "label=com.docker.compose.project=$project_name"
    )

    if (( ${#project_containers[@]} > 0 )); then
        docker rm -f "${project_containers[@]}" >/dev/null 2>&1 || true
    fi

    mapfile -t project_volumes < <(
        docker volume ls \
            --quiet \
            --filter "label=com.docker.compose.project=$project_name"
    )

    if (( ${#project_volumes[@]} > 0 )); then
        docker volume rm "${project_volumes[@]}" >/dev/null 2>&1 || true
    fi

    rm -rf "$cert_root"

    exit "$exit_code"
}

trap cleanup EXIT

query_opsight()
{
    local sql="$1"

    "${compose[@]}" exec -T opssight-db \
        psql \
        -U "$OPSSIGHT_DB_USER" \
        -d "$OPSSIGHT_DB_NAME" \
        -Atqc "$sql"
}

query_servicecore()
{
    local sql="$1"

    "${compose[@]}" exec -T servicecore-db \
        psql \
        -U "$SERVICECORE_DB_USER" \
        -d "$SERVICECORE_DB_NAME" \
        -Atqc "$sql"
}

wait_for_value()
{
    local description="$1"
    local command_name="$2"
    local sql="$3"
    local predicate="$4"
    local timeout_seconds="${5:-90}"
    local deadline=$((SECONDS + timeout_seconds))
    local value=""

    while (( SECONDS < deadline )); do
        value="$($command_name "$sql" 2>/dev/null || true)"

        if [[ -n "$value" ]] && [[ "$value" =~ $predicate ]]; then
            printf '%s: %s\n' "$description" "$value" >&2
            printf '%s' "$value"
            return 0
        fi

        sleep 2
    done

    printf 'error: timed out waiting for %s\n' "$description" >&2
    return 1
}

wait_for_http()
{
    local url="$1"
    local description="$2"
    local timeout_seconds="${3:-90}"
    local deadline=$((SECONDS + timeout_seconds))

    while (( SECONDS < deadline )); do
        if curl --fail --silent --show-error "$url" >/dev/null 2>&1; then
            printf '%s ready: %s\n' "$description" "$url"
            return 0
        fi

        sleep 2
    done

    printf 'error: timed out waiting for %s at %s\n' "$description" "$url" >&2
    return 1
}

wait_for_log()
{
    local service="$1"
    local pattern="$2"
    local description="$3"
    local timeout_seconds="${4:-90}"
    local deadline=$((SECONDS + timeout_seconds))

    while (( SECONDS < deadline )); do
        if "${compose[@]}" logs --no-color "$service" 2>/dev/null \
            | grep -E "$pattern" >/dev/null; then
            printf '%s observed in %s logs\n' "$description" "$service"
            return 0
        fi

        sleep 2
    done

    printf 'error: timed out waiting for %s in %s logs\n' \
        "$description" \
        "$service" \
        >&2
    return 1
}

if docker ps \
    --quiet \
    --filter label=com.docker.compose.project=nexusops-dev \
    | grep -q .; then
    cat >&2 <<'MESSAGE'
error: the nexusops-dev stack is running and uses the same local ports.
stop it without deleting volumes, then run the E2E scenario again:

  cd infra/compose
  docker compose stop
MESSAGE
    exit 1
fi

printf 'preparing isolated NexusOps E2E environment\n'

"${compose[@]}" down --remove-orphans >/dev/null 2>&1 || true

mapfile -t stale_volumes < <(
    docker volume ls \
        --quiet \
        --filter "label=com.docker.compose.project=$project_name"
)

if (( ${#stale_volumes[@]} > 0 )); then
    docker volume rm "${stale_volumes[@]}" >/dev/null
fi

rm -rf "$cert_root"

"$repo_root/infra/certs/dev-mtls.sh" \
    bootstrap \
    "$agent_id" \
    "$cert_root"

chmod 644 \
    "$cert_root/server/server.key.pem" \
    "$cert_root/agents/$agent_id/client.key.pem"

printf 'starting NexusOps stack\n'

"${compose[@]}" up \
    -d \
    --build \
    servicecore-db \
    opssight-db \
    rabbitmq \
    kafka \
    kafka-init \
    keycloak \
    servicecore-api \
    opssight-migrate \
    opssight-api \
    opssight-worker \
    opssight-scheduler \
    console \
    prometheus \
    grafana \
    sentinel-agent

wait_for_http \
    "http://127.0.0.1:${KEYCLOAK_PORT}/realms/nexusops/.well-known/openid-configuration" \
    "Keycloak"

wait_for_http \
    "http://127.0.0.1:${OPSSIGHT_API_PORT}/health" \
    "OpsSight"

wait_for_http \
    "http://127.0.0.1:${CONSOLE_PORT}/" \
    "Console"

printf 'creating E2E Keycloak operator\n'

"${compose[@]}" exec -T keycloak \
    /opt/keycloak/bin/kcadm.sh \
    config credentials \
    --server http://127.0.0.1:8080 \
    --realm master \
    --user "$KEYCLOAK_ADMIN_USER" \
    --password "$KEYCLOAK_ADMIN_PASSWORD" \
    >/dev/null

users_json="$(
    "${compose[@]}" exec -T keycloak \
        /opt/keycloak/bin/kcadm.sh \
        get users \
        -r nexusops \
        -q "username=$KEYCLOAK_DEV_USER"
)"

user_id="$(
    KEYCLOAK_DEV_USER="$KEYCLOAK_DEV_USER" \
    python3 -c '
import json
import os
import sys

username = os.environ["KEYCLOAK_DEV_USER"]

for user in json.load(sys.stdin):
    if user.get("username") == username:
        print(user["id"])
        break
' <<<"$users_json"
)"

if [[ -z "$user_id" ]]; then
    user_id="$(
        "${compose[@]}" exec -T keycloak \
            /opt/keycloak/bin/kcadm.sh \
            create users \
            -r nexusops \
            -s "username=$KEYCLOAK_DEV_USER" \
            -s enabled=true \
            -s firstName=NexusOps \
            -s lastName=E2E \
            -s "email=${KEYCLOAK_DEV_USER}@example.invalid" \
            -s emailVerified=true \
            -s 'requiredActions=[]' \
            -i
    )"
fi

"${compose[@]}" exec -T keycloak \
    /opt/keycloak/bin/kcadm.sh \
    set-password \
    -r nexusops \
    --userid "$user_id" \
    --new-password "$KEYCLOAK_DEV_USER_PASSWORD" \
    >/dev/null

"${compose[@]}" exec -T keycloak \
    /opt/keycloak/bin/kcadm.sh \
    add-roles \
    -r nexusops \
    --uid "$user_id" \
    --rolename "$KEYCLOAK_DEV_USER_ROLE" \
    >/dev/null

printf 'waiting for real SentinelAgent telemetry\n'

host_id="$(
    wait_for_value \
        "SentinelAgent host" \
        query_opsight \
        "SELECT h.id::text
         FROM hosts h
         JOIN agents a ON a.host_id = h.id
         WHERE a.agent_id = '$agent_id'
           AND h.name = '$host_name'
         LIMIT 1;" \
        '^[0-9a-fA-F-]{36}$' \
        120
)"

correlation_id="$(
    wait_for_value \
        "telemetry correlation ID" \
        query_opsight \
        "SELECT tb.correlation_id
         FROM telemetry_batches tb
         JOIN agents a ON a.id = tb.agent_record_id
         WHERE a.agent_id = '$agent_id'
           AND tb.acknowledged_through_sequence > 0
         ORDER BY tb.accepted_at DESC
         LIMIT 1;" \
        '^[0-9a-f]{64}$' \
        120
)"

telemetry_count="$(
    query_opsight \
        "SELECT COUNT(*)
         FROM telemetry
         WHERE host_id = '$host_id'
           AND labels ->> 'correlation_id' = '$correlation_id';"
)"

if [[ ! "$telemetry_count" =~ ^[1-9][0-9]*$ ]]; then
    printf 'error: telemetry was not persisted with the expected correlation ID\n' >&2
    exit 1
fi

printf 'telemetry persisted: host=%s correlation_id=%s rows=%s\n' \
    "$host_id" \
    "$correlation_id" \
    "$telemetry_count"

check_id="$(python3 -c 'import uuid; print(uuid.uuid4())')"
rule_id="$(python3 -c 'import uuid; print(uuid.uuid4())')"

query_opsight \
    "INSERT INTO checks (
         id,
         host_id,
         name,
         check_type,
         target,
         interval_seconds,
         timeout_seconds,
         consecutive_failures,
         next_run_at,
         enabled,
         created_at
     ) VALUES (
         '$check_id',
         '$host_id',
         '$check_name',
         'tcp',
         '$target',
         3600,
         1,
         0,
         '2100-01-01T00:00:00Z',
         TRUE,
         NOW()
     );

     INSERT INTO alert_rules (
         id,
         check_id,
         name,
         severity,
         failure_threshold,
         enabled,
         created_at
     ) VALUES (
         '$rule_id',
         '$check_id',
         '$rule_name',
         'critical',
         1,
         TRUE,
         NOW()
     );"

printf 'triggering deterministic monitoring failure\n'

"${compose[@]}" exec -T opssight-worker \
    python - \
    "$check_id" \
    "$correlation_id" <<'PY'
import sys

from opssight.tasks.checks import execute_check_task

execute_check_task.run(
    sys.argv[1],
    sys.argv[2],
)
PY

alert_id="$(
    wait_for_value \
        "open OpsSight alert" \
        query_opsight \
        "SELECT id::text
         FROM alerts
         WHERE alert_rule_id = '$rule_id'
           AND status = 'open'
           AND correlation_id = '$correlation_id'
         ORDER BY opened_at DESC
         LIMIT 1;" \
        '^[0-9a-fA-F-]{36}$' \
        60
)"

opened_event_id="$(
    wait_for_value \
        "AlertOpened outbox event" \
        query_opsight \
        "SELECT id::text
         FROM outbox_events
         WHERE aggregate_id = '$alert_id'
           AND event_type = 'nexusops.alert.opened'
           AND correlation_id = '$correlation_id'
         ORDER BY created_at DESC
         LIMIT 1;" \
        '^[0-9a-fA-F-]{36}$' \
        60
)"

printf 'publishing AlertOpened through the transactional outbox\n'

"${compose[@]}" exec -T opssight-worker \
    python - <<'PY'
from opssight.tasks.outbox import publish_outbox_events_task

publish_outbox_events_task.run()
PY

wait_for_value \
    "published AlertOpened outbox event" \
    query_opsight \
    "SELECT CASE
         WHEN published_at IS NOT NULL
         THEN 'published'
         ELSE ''
     END
     FROM outbox_events
     WHERE id = '$opened_event_id';" \
    '^published$' \
    60 \
    >/dev/null

incident_id="$(
    wait_for_value \
        "ServiceCore monitoring incident" \
        query_servicecore \
        "SELECT id::text
         FROM incidents
         WHERE source = 'MONITORING'
           AND source_alert_id = '$alert_id'
           AND correlation_id = '$correlation_id'
         LIMIT 1;" \
        '^[0-9a-fA-F-]{36}$' \
        90
)"

incident_count="$(
    query_servicecore \
        "SELECT COUNT(*)
         FROM incidents
         WHERE source = 'MONITORING'
           AND source_alert_id = '$alert_id';"
)"

if [[ "$incident_count" != "1" ]]; then
    printf 'error: expected exactly one monitoring incident, got %s\n' \
        "$incident_count" \
        >&2

    exit 1
fi

printf 're-delivering the same Kafka event to verify idempotency\n'

opened_event_json="$(
    query_opsight \
        "SELECT event_data::text
         FROM outbox_events
         WHERE id = '$opened_event_id';"
)"

printf '%s\n' "$opened_event_json" \
    | "${compose[@]}" exec -T kafka \
        /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server kafka:19092 \
        --topic "$NEXUSOPS_KAFKA_ALERT_TOPIC" \
        >/dev/null

wait_for_log \
    servicecore-api \
    "event_id=${opened_event_id}.*result=DUPLICATE" \
    "duplicate Kafka delivery" \
    60

incident_count_after_duplicate="$(
    query_servicecore \
        "SELECT COUNT(*)
         FROM incidents
         WHERE source = 'MONITORING'
           AND source_alert_id = '$alert_id';"
)"

processed_event_count="$(
    query_servicecore \
        "SELECT COUNT(*)
         FROM processed_events
         WHERE event_id = '$opened_event_id';"
)"

if [[ "$incident_count_after_duplicate" != "1" ]]; then
    printf 'error: duplicate Kafka delivery created another incident\n' >&2
    exit 1
fi

if [[ "$processed_event_count" != "1" ]]; then
    printf 'error: duplicate event identity was not preserved\n' >&2
    exit 1
fi

printf 'starting recovery target\n'

"${compose[@]}" \
    --profile recovery-target \
    up \
    -d \
    --wait \
    e2e-target

printf 'triggering deterministic recovery\n'

"${compose[@]}" exec -T opssight-worker \
    python - \
    "$check_id" \
    "$correlation_id" <<'PY'
import sys

from opssight.tasks.checks import execute_check_task

execute_check_task.run(
    sys.argv[1],
    sys.argv[2],
)
PY

wait_for_value \
    "recovered OpsSight alert" \
    query_opsight \
    "SELECT status
     FROM alerts
     WHERE id = '$alert_id'
       AND correlation_id = '$correlation_id';" \
    '^recovered$' \
    60 \
    >/dev/null

recovered_event_id="$(
    wait_for_value \
        "AlertRecovered outbox event" \
        query_opsight \
        "SELECT id::text
         FROM outbox_events
         WHERE aggregate_id = '$alert_id'
           AND event_type = 'nexusops.alert.recovered'
           AND correlation_id = '$correlation_id'
         ORDER BY created_at DESC
         LIMIT 1;" \
        '^[0-9a-fA-F-]{36}$' \
        60
)"

printf 'publishing AlertRecovered through the transactional outbox\n'

"${compose[@]}" exec -T opssight-worker \
    python - <<'PY'
from opssight.tasks.outbox import publish_outbox_events_task

publish_outbox_events_task.run()
PY

wait_for_value \
    "ServiceCore recovery context" \
    query_servicecore \
    "SELECT CASE
         WHEN monitoring_recovered_at IS NOT NULL
          AND correlation_id = '$correlation_id'
         THEN 'recovered'
         ELSE ''
     END
     FROM incidents
     WHERE id = '$incident_id';" \
    '^recovered$' \
    90 \
    >/dev/null

recovered_incident_id="$(
    query_servicecore \
        "SELECT id::text
         FROM incidents
         WHERE source_alert_id = '$alert_id'
           AND monitoring_recovered_at IS NOT NULL;"
)"

if [[ "$recovered_incident_id" != "$incident_id" ]]; then
    printf 'error: recovery did not update the existing ServiceCore incident\n' >&2
    exit 1
fi

wait_for_value \
    "published AlertRecovered outbox event" \
    query_opsight \
    "SELECT CASE
         WHEN published_at IS NOT NULL
         THEN 'published'
         ELSE ''
     END
     FROM outbox_events
     WHERE id = '$recovered_event_id';" \
    '^published$' \
    60 \
    >/dev/null

printf 'verifying recovered state through the real Console\n'

export NEXUSOPS_E2E_CONSOLE_URL="http://127.0.0.1:${CONSOLE_PORT}"
export NEXUSOPS_E2E_USER="$KEYCLOAK_DEV_USER"
export NEXUSOPS_E2E_PASSWORD="$KEYCLOAK_DEV_USER_PASSWORD"
export NEXUSOPS_E2E_HOST_NAME="$host_name"
export NEXUSOPS_E2E_ALERT_ID="$alert_id"
export NEXUSOPS_E2E_INCIDENT_ID="$incident_id"
export NEXUSOPS_E2E_CORRELATION_ID="$correlation_id"

(
    cd "$repo_root/console"

    npx playwright test \
        e2e/live-recovery.live.ts \
        --config playwright.live.config.ts
)

cat <<SUMMARY

NexusOps E2E recovery scenario passed.

agent_id=$agent_id
host_id=$host_id
telemetry_correlation_id=$correlation_id
alert_id=$alert_id
incident_id=$incident_id
opened_event_id=$opened_event_id
recovered_event_id=$recovered_event_id
SUMMARY