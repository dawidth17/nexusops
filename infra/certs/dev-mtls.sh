#!/usr/bin/env bash

set -euo pipefail

script_dir="$(
    cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
    pwd
)"

default_output_root="${script_dir}/generated"
openssl_bin="${OPENSSL_BIN:-openssl}"

usage()
{
    cat <<'EOF'
Usage:
  dev-mtls.sh init-ca [output-root]
  dev-mtls.sh issue-server [output-root]
  dev-mtls.sh rotate-server [output-root]
  dev-mtls.sh issue-agent <agent-id> [output-root]
  dev-mtls.sh rotate-agent <agent-id> [output-root]
  dev-mtls.sh bootstrap <agent-id> [output-root]
EOF
}

fail()
{
    printf 'error: %s\n' "$1" >&2
    exit 1
}

require_openssl()
{
    command -v "$openssl_bin" >/dev/null 2>&1 \
        || fail "openssl was not found"
}

validate_agent_id()
{
    local agent_id="$1"

    if [[ ! "$agent_id" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$ ]]; then
        fail "agent id must contain 1-200 letters, digits, dots, underscores or hyphens"
    fi
}

random_serial()
{
    printf '0x%s' "$("$openssl_bin" rand -hex 16)"
}

init_ca()
{
    local output_root="$1"

    local ca_dir="${output_root}/ca"
    local ca_key="${ca_dir}/ca.key.pem"
    local ca_cert="${ca_dir}/ca.cert.pem"

    mkdir -p "$ca_dir"

    if [[ -f "$ca_key" && -f "$ca_cert" ]]; then
        printf 'development ca already exists: %s\n' "$ca_cert"
        return
    fi

    if [[ -e "$ca_key" || -e "$ca_cert" ]]; then
        fail "development ca is incomplete; remove the output directory and retry"
    fi

    umask 077

    "$openssl_bin" genpkey \
        -algorithm RSA \
        -pkeyopt rsa_keygen_bits:3072 \
        -out "$ca_key"

    "$openssl_bin" req \
        -x509 \
        -new \
        -sha256 \
        -days 3650 \
        -key "$ca_key" \
        -out "$ca_cert" \
        -subj "/CN=NexusOps Development CA" \
        -addext "basicConstraints=critical,CA:TRUE" \
        -addext "keyUsage=critical,keyCertSign,cRLSign" \
        -addext "subjectKeyIdentifier=hash"

    chmod 600 "$ca_key"
    chmod 644 "$ca_cert"

    printf 'created development ca: %s\n' "$ca_cert"
}

issue_server()
{
    local output_root="$1"
    local force="$2"

    init_ca "$output_root"

    local ca_key="${output_root}/ca/ca.key.pem"
    local ca_cert="${output_root}/ca/ca.cert.pem"

    local server_dir="${output_root}/server"
    local server_key="${server_dir}/server.key.pem"
    local server_cert="${server_dir}/server.cert.pem"
    local server_csr="${server_dir}/server.csr.pem"
    local extension_file="${server_dir}/server.ext"

    mkdir -p "$server_dir"

    if [[ "$force" != "true" && -f "$server_key" && -f "$server_cert" ]]; then
        printf 'server certificate already exists: %s\n' "$server_cert"
        return
    fi

    rm -f \
        "$server_key" \
        "$server_cert" \
        "$server_csr" \
        "$extension_file"

    umask 077

    "$openssl_bin" genpkey \
        -algorithm RSA \
        -pkeyopt rsa_keygen_bits:3072 \
        -out "$server_key"

    "$openssl_bin" req \
        -new \
        -sha256 \
        -key "$server_key" \
        -out "$server_csr" \
        -subj "/CN=localhost"

    cat >"$extension_file" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,IP:127.0.0.1
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
EOF

    "$openssl_bin" x509 \
        -req \
        -sha256 \
        -days 825 \
        -in "$server_csr" \
        -CA "$ca_cert" \
        -CAkey "$ca_key" \
        -set_serial "$(random_serial)" \
        -extfile "$extension_file" \
        -out "$server_cert"

    rm -f \
        "$server_csr" \
        "$extension_file"

    chmod 600 "$server_key"
    chmod 644 "$server_cert"

    "$openssl_bin" verify \
        -CAfile "$ca_cert" \
        -purpose sslserver \
        "$server_cert"

    printf 'issued server certificate: %s\n' "$server_cert"
}

issue_agent()
{
    local agent_id="$1"
    local output_root="$2"
    local force="$3"

    validate_agent_id "$agent_id"
    init_ca "$output_root"

    local ca_key="${output_root}/ca/ca.key.pem"
    local ca_cert="${output_root}/ca/ca.cert.pem"

    local agent_dir="${output_root}/agents/${agent_id}"
    local agent_key="${agent_dir}/client.key.pem"
    local agent_cert="${agent_dir}/client.cert.pem"
    local agent_csr="${agent_dir}/client.csr.pem"
    local extension_file="${agent_dir}/client.ext"

    mkdir -p "$agent_dir"

    if [[ "$force" != "true" && -f "$agent_key" && -f "$agent_cert" ]]; then
        printf 'agent certificate already exists: %s\n' "$agent_cert"
        return
    fi

    rm -f \
        "$agent_key" \
        "$agent_cert" \
        "$agent_csr" \
        "$extension_file"

    umask 077

    "$openssl_bin" genpkey \
        -algorithm RSA \
        -pkeyopt rsa_keygen_bits:3072 \
        -out "$agent_key"

    "$openssl_bin" req \
        -new \
        -sha256 \
        -key "$agent_key" \
        -out "$agent_csr" \
        -subj "/CN=${agent_id}"

    cat >"$extension_file" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
EOF

    "$openssl_bin" x509 \
        -req \
        -sha256 \
        -days 365 \
        -in "$agent_csr" \
        -CA "$ca_cert" \
        -CAkey "$ca_key" \
        -set_serial "$(random_serial)" \
        -extfile "$extension_file" \
        -out "$agent_cert"

    rm -f \
        "$agent_csr" \
        "$extension_file"

    chmod 600 "$agent_key"
    chmod 644 "$agent_cert"

    "$openssl_bin" verify \
        -CAfile "$ca_cert" \
        -purpose sslclient \
        "$agent_cert"

    printf 'issued agent certificate: %s\n' "$agent_cert"
}

main()
{
    require_openssl

    local command="${1:-}"

    case "$command" in
        init-ca)
            local output_root="${2:-$default_output_root}"

            init_ca "$output_root"
            ;;

        issue-server)
            local output_root="${2:-$default_output_root}"

            issue_server "$output_root" "false"
            ;;

        rotate-server)
            local output_root="${2:-$default_output_root}"

            issue_server "$output_root" "true"
            ;;

        issue-agent)
            [[ $# -ge 2 ]] || {
                usage
                exit 1
            }

            local agent_id="$2"
            local output_root="${3:-$default_output_root}"

            issue_agent \
                "$agent_id" \
                "$output_root" \
                "false"
            ;;

        rotate-agent)
            [[ $# -ge 2 ]] || {
                usage
                exit 1
            }

            local agent_id="$2"
            local output_root="${3:-$default_output_root}"

            issue_agent \
                "$agent_id" \
                "$output_root" \
                "true"
            ;;

        bootstrap)
            [[ $# -ge 2 ]] || {
                usage
                exit 1
            }

            local agent_id="$2"
            local output_root="${3:-$default_output_root}"

            init_ca "$output_root"

            issue_server \
                "$output_root" \
                "false"

            issue_agent \
                "$agent_id" \
                "$output_root" \
                "false"
            ;;

        *)
            usage
            exit 1
            ;;
    esac
}

main "$@"