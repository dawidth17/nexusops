#!/usr/bin/env bash

set -euo pipefail

script_dir="$(
    cd "$(
        dirname "${BASH_SOURCE[0]}"
    )"
    pwd
)"

compose_dir="$script_dir/../compose"

env_file="${NEXUSOPS_COMPOSE_ENV_FILE:-$compose_dir/.env}"

if [[ ! -f "$env_file" ]]; then
    echo "compose environment file not found: $env_file" >&2
    exit 1
fi

set -a
source "$env_file"
set +a

: "${KEYCLOAK_ADMIN_USER:?KEYCLOAK_ADMIN_USER is required}"
: "${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD is required}"
: "${KEYCLOAK_DEV_USER:?KEYCLOAK_DEV_USER is required}"
: "${KEYCLOAK_DEV_USER_PASSWORD:?KEYCLOAK_DEV_USER_PASSWORD is required}"
: "${KEYCLOAK_DEV_USER_ROLE:?KEYCLOAK_DEV_USER_ROLE is required}"

case "$KEYCLOAK_DEV_USER_ROLE" in
    viewer|operator|employee|technician|manager|admin)
        ;;
    *)
        echo \
            "invalid KEYCLOAK_DEV_USER_ROLE: $KEYCLOAK_DEV_USER_ROLE" \
            >&2
        exit 1
        ;;
esac

compose=(
    docker compose
    --env-file "$env_file"
    -f "$compose_dir/compose.yml"
)

"${compose[@]}" exec \
    -T \
    keycloak \
    /opt/keycloak/bin/kcadm.sh \
    config credentials \
    --server http://127.0.0.1:8080 \
    --realm master \
    --user "$KEYCLOAK_ADMIN_USER" \
    --password "$KEYCLOAK_ADMIN_PASSWORD" \
    >/dev/null

users_json="$(
    "${compose[@]}" exec \
        -T \
        keycloak \
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
users = json.load(sys.stdin)

for user in users:
    if user.get("username") == username:
        print(user["id"])
        break
' <<<"$users_json"
)"

dev_email="${KEYCLOAK_DEV_USER}@example.invalid"

if [[ -z "$user_id" ]]; then
    user_id="$(
        "${compose[@]}" exec \
            -T \
            keycloak \
            /opt/keycloak/bin/kcadm.sh \
            create users \
            -r nexusops \
            -s "username=$KEYCLOAK_DEV_USER" \
            -s enabled=true \
            -s firstName=NexusOps \
            -s lastName=Development \
            -s "email=$dev_email" \
            -s emailVerified=true \
            -s 'requiredActions=[]' \
            -i
    )"
else
    "${compose[@]}" exec \
        -T \
        keycloak \
        /opt/keycloak/bin/kcadm.sh \
        update "users/$user_id" \
        -r nexusops \
        -s enabled=true \
        -s firstName=NexusOps \
        -s lastName=Development \
        -s "email=$dev_email" \
        -s emailVerified=true \
        -s 'requiredActions=[]'
fi

"${compose[@]}" exec \
    -T \
    keycloak \
    /opt/keycloak/bin/kcadm.sh \
    set-password \
    -r nexusops \
    --userid "$user_id" \
    --new-password "$KEYCLOAK_DEV_USER_PASSWORD"

"${compose[@]}" exec \
    -T \
    keycloak \
    /opt/keycloak/bin/kcadm.sh \
    add-roles \
    -r nexusops \
    --uid "$user_id" \
    --rolename "$KEYCLOAK_DEV_USER_ROLE"

echo \
    "keycloak development user ready: $KEYCLOAK_DEV_USER ($KEYCLOAK_DEV_USER_ROLE)"