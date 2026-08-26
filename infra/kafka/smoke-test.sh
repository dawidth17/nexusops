#!/usr/bin/env bash

set -euo pipefail

script_dir="$(
    cd "$(
        dirname "${BASH_SOURCE[0]}"
    )"
    pwd
)"

repo_root="$(
    cd "$script_dir/../.."
    pwd
)"

compose_dir="$repo_root/infra/compose"

env_file="${NEXUSOPS_COMPOSE_ENV_FILE:-$compose_dir/.env}"

if [[ ! -f "$env_file" ]]; then
    echo "compose environment file not found: $env_file" >&2
    exit 1
fi

set -a
source "$env_file"
set +a

compose=(
    docker compose
    --env-file "$env_file"
    -f "$compose_dir/compose.yml"
)

event_file="$repo_root/contracts/events/v1/examples/alert-opened.json"

if [[ ! -f "$event_file" ]]; then
    echo "smoke event not found: $event_file" >&2
    exit 1
fi

run_id="${GITHUB_RUN_ID:-$$}"

smoke_topic="nexusops.smoke.event-contract-${run_id}.v1"

bootstrap_server="127.0.0.1:19092"

cleanup() {
    "${compose[@]}" exec \
        -T \
        kafka \
        /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$bootstrap_server" \
        --delete \
        --if-exists \
        --topic "$smoke_topic" \
        >/dev/null 2>&1 \
        || true
}

trap cleanup EXIT

"${compose[@]}" exec \
    -T \
    kafka \
    /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$bootstrap_server" \
    --create \
    --topic "$smoke_topic" \
    --partitions 1 \
    --replication-factor 1 \
    >/dev/null

compact_event="$(
    EVENT_FILE="$event_file" \
    python3 - <<'PY'
import json
import os
from pathlib import Path

path = Path(os.environ["EVENT_FILE"])

with path.open(encoding="utf-8") as file:
    event = json.load(file)

print(
    json.dumps(
        event,
        separators=(",", ":"),
        sort_keys=True,
    )
)
PY
)"

printf '%s\n' "$compact_event" \
    | "${compose[@]}" exec \
        -T \
        kafka \
        /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server "$bootstrap_server" \
        --topic "$smoke_topic"

received_event="$(
    "${compose[@]}" exec \
        -T \
        kafka \
        /opt/kafka/bin/kafka-console-consumer.sh \
        --bootstrap-server "$bootstrap_server" \
        --topic "$smoke_topic" \
        --from-beginning \
        --max-messages 1 \
        --timeout-ms 15000
)"

EXPECTED_EVENT="$compact_event" \
RECEIVED_EVENT="$received_event" \
python3 - <<'PY'
import json
import os

expected = json.loads(
    os.environ["EXPECTED_EVENT"]
)

received = json.loads(
    os.environ["RECEIVED_EVENT"]
)

if received != expected:
    raise SystemExit(
        "Kafka smoke test consumed a different event"
    )

print("Kafka producer -> consumer smoke test passed")
PY