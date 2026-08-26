# SentinelAgent

SentinelAgent is the Linux monitoring agent used by NexusOps.

The project is split into five main parts:

- `libsysprobe` - a C17 library for low-level Linux system data collection
- `sentinel_runtime` - C++20 scheduling, concurrency, lifecycle and network diagnostics
- `sentinel_storage` - C++20 SQLite durable telemetry buffering
- `sentinel_telemetry_codec` - Protobuf serialization for collected telemetry
- `sentinel_grpc_transport` - gRPC delivery to OpsSight with acknowledgement, retry, and mTLS support

## Current collectors

The current version collects:

- CPU usage from `/proc/stat`
- memory totals and available memory from `/proc/meminfo`
- filesystem capacity through `statvfs()`
- system uptime from `/proc/uptime`
- network traffic counters from `/proc/net/dev`
- IPv4 and IPv6 interface addresses through `getifaddrs()`
- process snapshots from `/proc/<pid>/status`

The collectors do not execute shell commands to obtain core metrics.

## Concurrency

The C++ runtime contains a fixed-size thread pool with a bounded work queue.

The scheduler submits periodic collector and telemetry transport tasks to the pool.

The bounded queue prevents unlimited in-memory growth when work is produced faster than workers can process it.

Missed scheduler intervals are skipped instead of being submitted as a large catch-up burst.

## SQLite durable spool

Collected telemetry is serialized as Protobuf and stored in a local SQLite spool before transmission.

Each record contains:

- an increasing sequence number
- UTC capture time
- telemetry kind
- payload format
- binary telemetry payload

The spool is bounded and its schema is versioned.

Records survive agent restarts and transport failures.

Records are removed only after OpsSight acknowledges the corresponding telemetry batch.

This allows the agent to continue collecting data while OpsSight is unavailable and resend the stored records later.

## OpsSight telemetry transport

SentinelAgent sends telemetry to OpsSight using the shared `telemetry.v1` gRPC contract.

The default endpoint is:

```text
127.0.0.1:50051
```

It can be changed with:

```text
NEXUSOPS_SENTINEL_OPSSIGHT_ENDPOINT
```

The agent sends a heartbeat before telemetry delivery.

The heartbeat contains:

- agent ID
- hostname
- agent version

The default agent ID is derived from the local hostname:

```text
sentinel-<hostname>
```

It can be overridden with:

```text
NEXUSOPS_SENTINEL_AGENT_ID
```

The hostname can also be overridden with:

```text
NEXUSOPS_SENTINEL_HOSTNAME
```

Telemetry is read from SQLite in sequence order and sent in bounded batches.

A batch is deleted from the spool only after an acknowledgement containing the expected batch ID and sequence number is received.

If delivery fails before acknowledgement, the records remain in SQLite.

The next transport attempt reads the same records again and retransmits the same deterministic batch.

This provides durable retry behavior without losing telemetry when OpsSight is temporarily unavailable.

## Mutual TLS

SentinelAgent supports mutual TLS for its gRPC connection to OpsSight.

Enable it with:

```text
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_ENABLED=true
```

When mTLS is enabled, SentinelAgent requires:

```text
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CA_CERT_PATH
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_CERT_PATH
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_KEY_PATH
```

For example:

```bash
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_ENABLED=true \
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CA_CERT_PATH=infra/certs/generated/ca/ca.cert.pem \
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_CERT_PATH=infra/certs/generated/agents/sentinel-local/client.cert.pem \
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_KEY_PATH=infra/certs/generated/agents/sentinel-local/client.key.pem \
./sentinel-agent/build/sentinel-agent \
  --run-seconds 12
```

SentinelAgent validates the OpsSight server certificate using the configured CA.

Normal TLS hostname or IP validation is also performed against the configured OpsSight endpoint.

The client certificate common name represents the authenticated SentinelAgent identity.

For example:

```text
CN=sentinel-local
```

must be used with:

```text
NEXUSOPS_SENTINEL_AGENT_ID=sentinel-local
```

OpsSight validates this binding server-side.

Changing only the telemetry payload agent ID therefore cannot impersonate another enrolled agent.

mTLS configuration is fail-closed.

If mTLS is enabled but a required certificate path is missing, unreadable, or empty, SentinelAgent stops instead of falling back to insecure gRPC transport.

Development certificate generation, agent enrollment, and certificate rotation are documented in:

```text
docs/security/mtls.md
```

Generated certificates and private keys are not committed to Git.

## Telemetry flow

```text
collectors
    |
    v
protobuf encoding
    |
    v
sqlite spool
    |
    v
grpc transport
    |
    | mTLS
    v
opssight
    |
    v
ack
    |
    v
sqlite acknowledgement
```

## Lifecycle

SentinelAgent runs as a long-lived Linux service.

`SIGTERM` and `SIGINT` trigger graceful shutdown.

`SIGHUP` is consumed as a reload request.

Shutdown stops the scheduler, drains accepted worker tasks and performs a final telemetry flush attempt before the SQLite spool is closed.

A transport failure does not terminate the agent.

## Network diagnostics

SentinelAgent provides explicit diagnostic commands for connectivity troubleshooting.

DNS resolution uses the operating system resolver through `getaddrinfo()`.

TCP checks use POSIX sockets and a bounded connect timeout.

HTTP checks send a direct HTTP request and validate the returned status line.

HTTP status codes from 200 through 399 are considered healthy.

HTTPS checks use TLS certificate-chain and hostname verification.

TLS certificate checks report certificate subject, issuer and remaining days before expiry.

The diagnostic implementation does not invoke shell tools such as `curl`, `nc`, `telnet` or `openssl`.

### DNS

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose dns localhost
```

### TCP

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose tcp 127.0.0.1 5432
```

### HTTP

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose http http://127.0.0.1:8080/health
```

### HTTPS

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose http https://example.internal/health
```

### TLS certificate

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose tls example.internal 443
```

## Build quality

The project enables compiler warnings with:

```text
-Wall
-Wextra
-Wpedantic
```

The CI pipeline includes:

```text
regular build and tests
gRPC transport tests
offline spool and retry validation
AddressSanitizer + UndefinedBehaviorSanitizer
clang-tidy + cppcheck
Debian package build and installation smoke test
```

The gRPC transport tests verify:

- successful telemetry acknowledgement
- retention of records when OpsSight is unavailable
- reconnect and ordered retransmission
- stable batch IDs across retry attempts

The NexusOps integration tests additionally verify authenticated gRPC delivery using mTLS.

AddressSanitizer is used to detect memory-safety errors.

UndefinedBehaviorSanitizer detects undefined runtime behavior.

Static analysis is performed against the project compilation database.

## CMake presets

From the `sentinel-agent` directory:

### Development

```bash
cmake --preset dev
cmake --build --preset dev
ctest --preset dev
```

### Sanitizers

```bash
cmake --preset sanitizers
cmake --build --preset sanitizers
ctest --preset sanitizers
```

### Debian package

```bash
cmake --preset package
cmake --build --preset package

cpack \
  --config build/package/CPackConfig.cmake \
  -G DEB
```

Generated packages are written to:

```text
build/package/packages/
```

A package name follows the Debian format:

```text
nexusops-sentinel-agent_<version>-<revision>_<architecture>.deb
```

## Debian package contents

The package installs:

```text
/usr/bin/nexusops-sentinel-agent
/usr/lib/systemd/system/nexusops-sentinel.service
/etc/nexusops-sentinel/sentinel.conf
/usr/share/doc/nexusops-sentinel-agent/README.md
```

The configuration file is managed as a Debian conffile.

Package removal preserves the configuration file.

Package purge removes the configuration file.

The SQLite state directory is not automatically deleted during package removal or purge.

## Agent configuration

The packaged configuration file is:

```text
/etc/nexusops-sentinel/sentinel.conf
```

The default configuration contains:

```text
NEXUSOPS_SENTINEL_SPOOL_PATH=/var/lib/nexusops-sentinel/telemetry.db
NEXUSOPS_SENTINEL_OPSSIGHT_ENDPOINT=127.0.0.1:50051
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_ENABLED=false
```

mTLS can be enabled after the agent has been enrolled and its certificate material has been installed.

The following environment variables are supported:

```text
NEXUSOPS_SENTINEL_SPOOL_PATH
NEXUSOPS_SENTINEL_OPSSIGHT_ENDPOINT
NEXUSOPS_SENTINEL_AGENT_ID
NEXUSOPS_SENTINEL_HOSTNAME
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_ENABLED
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CA_CERT_PATH
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_CERT_PATH
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_KEY_PATH
```

The agent ID and hostname overrides are optional.

When mTLS is enabled, the CA certificate, client certificate, and client private key paths are required.

The configured agent ID must match the identity in the client certificate.

## Installing the package

Build the package first.

Then:

```bash
sudo dpkg \
  -i \
  build/package/packages/nexusops-sentinel-agent_*.deb
```

Verify the installed files:

```bash
command -v nexusops-sentinel-agent

cat \
  /etc/nexusops-sentinel/sentinel.conf

systemd-analyze verify \
  /usr/lib/systemd/system/nexusops-sentinel.service
```

## Starting the packaged service

```bash
sudo systemctl start \
  nexusops-sentinel.service
```

Check status:

```bash
sudo systemctl status \
  nexusops-sentinel.service \
  --no-pager
```

Check logs:

```bash
sudo journalctl \
  -u nexusops-sentinel.service \
  -n 50 \
  --no-pager
```

Enable automatic startup if desired:

```bash
sudo systemctl enable \
  nexusops-sentinel.service
```

## Stopping the packaged service

```bash
sudo systemctl stop \
  nexusops-sentinel.service
```

Graceful shutdown should be visible in the journal.

## Removing the package

Remove the program while preserving its configuration:

```bash
sudo dpkg \
  -r \
  nexusops-sentinel-agent
```

Purge the remaining package configuration:

```bash
sudo dpkg \
  -P \
  nexusops-sentinel-agent
```

## Local static analysis

Configure the compilation database:

```bash
cmake \
  -S sentinel-agent \
  -B sentinel-agent/build/analysis \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON \
  -DSENTINEL_BUILD_TESTS=OFF
```

Run clang-tidy:

```bash
find \
  sentinel-agent/app \
  sentinel-agent/src \
  -type f \
  \( -name '*.c' -o -name '*.cpp' \) \
  -print0 \
  | xargs -0 clang-tidy \
      -p sentinel-agent/build/analysis
```

Run cppcheck:

```bash
cppcheck \
  --project=sentinel-agent/build/analysis/compile_commands.json \
  --enable=warning,performance,portability \
  --error-exitcode=1 \
  --inline-suppr \
  --suppress=missingIncludeSystem
```

## Optional local debugging

Valgrind can be used as an additional local memory check:

```bash
valgrind \
  --leak-check=full \
  --show-leak-kinds=all \
  --error-exitcode=1 \
  ./sentinel-agent/build/dev/sentinel-agent \
  --run-seconds 2
```

GDB can be used for interactive debugging:

```bash
gdb \
  --args \
  ./sentinel-agent/build/dev/sentinel-agent \
  --run-seconds 5
```

Sanitizers remain the automated CI memory-safety gate.

## Local state

The spool path is resolved in this order:

```text
NEXUSOPS_SENTINEL_SPOOL_PATH
XDG_STATE_HOME
HOME/.local/state/nexusops-sentinel
/tmp fallback
```

The packaged systemd service defaults to:

```text
/var/lib/nexusops-sentinel/telemetry.db
```

The value can be overridden through:

```text
/etc/nexusops-sentinel/sentinel.conf
```

## Offline behavior

OpsSight does not need to be available for SentinelAgent to continue collecting telemetry.

For a local offline test:

```bash
NEXUSOPS_SENTINEL_SPOOL_PATH=/tmp/nexusops-sentinel-offline.db \
NEXUSOPS_SENTINEL_OPSSIGHT_ENDPOINT=127.0.0.1:59999 \
./sentinel-agent/build/sentinel-agent \
  --run-seconds 7
```

The output should contain:

```text
transport status=transport_error
```

The SQLite spool should still contain telemetry records after the process exits.

## Local OpsSight mTLS integration

Generate a development CA, OpsSight server certificate, and SentinelAgent certificate from the repository root:

```bash
bash infra/certs/dev-mtls.sh \
  bootstrap \
  sentinel-local
```

Start OpsSight from the `opssight` directory:

```bash
set -a
source ../infra/compose/.env
set +a

export OPSSIGHT_DB_NAME=opssight

export OPSSIGHT_GRPC_MTLS_ENABLED=true

export OPSSIGHT_GRPC_MTLS_CA_CERTIFICATE_PATH=\
../infra/certs/generated/ca/ca.cert.pem

export OPSSIGHT_GRPC_MTLS_SERVER_CERTIFICATE_PATH=\
../infra/certs/generated/server/server.cert.pem

export OPSSIGHT_GRPC_MTLS_SERVER_PRIVATE_KEY_PATH=\
../infra/certs/generated/server/server.key.pem

export OPSSIGHT_GRPC_MTLS_ALLOWED_AGENT_IDS=\
sentinel-local

uv run uvicorn \
  opssight.main:app \
  --host 127.0.0.1 \
  --port 8000
```

Then, from the repository root, run SentinelAgent:

```bash
NEXUSOPS_SENTINEL_SPOOL_PATH=/tmp/nexusops-sentinel-e2e.db \
NEXUSOPS_SENTINEL_OPSSIGHT_ENDPOINT=127.0.0.1:50051 \
NEXUSOPS_SENTINEL_AGENT_ID=sentinel-local \
NEXUSOPS_SENTINEL_HOSTNAME=sentinel-local-host \
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_ENABLED=true \
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CA_CERT_PATH=infra/certs/generated/ca/ca.cert.pem \
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_CERT_PATH=infra/certs/generated/agents/sentinel-local/client.cert.pem \
NEXUSOPS_SENTINEL_OPSSIGHT_MTLS_CLIENT_KEY_PATH=infra/certs/generated/agents/sentinel-local/client.key.pem \
./sentinel-agent/build/sentinel-agent \
  --run-seconds 12
```

Successful startup reports:

```text
transport_security=mtls
```

Successful delivery produces output similar to:

```text
transport status=ok sent_records=2 acknowledged_records=2
```

After successful acknowledgement, the spool should be empty:

```bash
sqlite3 \
  /tmp/nexusops-sentinel-e2e.db \
  "SELECT COUNT(*) FROM telemetry_spool;"
```

Expected result:

```text
0
```

The complete certificate enrollment and rotation procedure is documented in:

```text
docs/security/mtls.md
```

## Requirements

- Linux
- GCC or Clang
- CMake 3.28+
- Ninja
- SQLite 3 development library
- OpenSSL development library
- Protobuf development library and compiler
- gRPC C++ development library
- gRPC Protobuf compiler plugin
- pthreads
- clang-tidy for static analysis
- cppcheck for static analysis
- dpkg tools for Debian packaging

On Ubuntu, the telemetry transport dependencies include:

```text
libgrpc++-dev
libprotobuf-dev
protobuf-compiler
protobuf-compiler-grpc
```

## Run interactively

From the repository root:

```bash
./sentinel-agent/build/sentinel-agent
```

Pressing `Ctrl+C` triggers graceful shutdown.

For a bounded local run:

```bash
./sentinel-agent/build/sentinel-agent \
  --run-seconds 5
```

## Manual collector comparison

```bash
head -n 1 /proc/stat
grep -E 'MemTotal|MemAvailable' /proc/meminfo
df -B1 /
cat /proc/uptime
cat /proc/net/dev
cat /proc/self/status
```