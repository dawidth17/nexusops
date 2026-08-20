# SentinelAgent

SentinelAgent is the Linux monitoring agent used by NexusOps.

The project is split into three main parts:

- `libsysprobe` - a C17 library for low-level Linux system data collection
- `sentinel_runtime` - C++20 scheduling, concurrency, lifecycle and network diagnostics
- `sentinel_storage` - C++20 SQLite durable telemetry buffering

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

The scheduler submits periodic collector tasks to the pool.

The bounded queue prevents unlimited in-memory growth when work is produced faster than workers can process it.

Missed scheduler intervals are skipped instead of being submitted as a large catch-up burst.

## SQLite durable spool

Collected telemetry is stored in a local SQLite spool.

Each record contains an increasing sequence number, UTC capture time, telemetry kind and telemetry payload.

The spool is bounded and its schema is versioned.

Telemetry remains stored across agent restarts until a later transport milestone acknowledges it.

## Lifecycle

SentinelAgent runs as a long-lived Linux service.

`SIGTERM` and `SIGINT` trigger graceful shutdown.

`SIGHUP` is consumed as a reload request.

Shutdown stops the scheduler, drains accepted worker tasks and closes SQLite cleanly.

## Network diagnostics

SentinelAgent provides explicit diagnostic commands for connectivity troubleshooting.

DNS resolution uses the operating system resolver through `getaddrinfo()`.

TCP checks use POSIX sockets and a bounded connect timeout.

HTTP checks send a direct HTTP request and validate the returned status line.

HTTP status codes from 200 through 399 are considered healthy.

HTTPS checks use TLS certificate-chain and hostname verification.

TLS certificate checks report certificate subject, issuer and remaining days before expiry.

The diagnostic implementation does not invoke shell tools such as `curl`, `nc`, `telnet` or `openssl`.

The `openssl` command used in CI exists only to create a temporary local TLS test server.

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

A custom CA certificate can be supplied as the final argument:

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose http \
  https://example.internal/health \
  /path/to/internal-ca.pem
```

### TLS certificate

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose tls example.internal 443
```

With a custom CA:

```bash
./sentinel-agent/build/sentinel-agent \
  diagnose tls \
  example.internal \
  443 \
  /path/to/internal-ca.pem
```

## Local state

The spool path is resolved in this order:

```text
NEXUSOPS_SENTINEL_SPOOL_PATH
XDG_STATE_HOME
HOME/.local/state/nexusops-sentinel
/tmp fallback
```

The systemd service stores the spool at:

```text
/var/lib/nexusops-sentinel/telemetry.db
```

## Requirements

- Linux
- GCC or Clang
- CMake 3.28+
- Ninja
- SQLite 3 development library
- OpenSSL development library
- pthreads

## Configure

```bash
cmake \
  -S sentinel-agent \
  -B sentinel-agent/build \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Debug \
  -DSENTINEL_BUILD_TESTS=ON
```

## Build

```bash
cmake \
  --build sentinel-agent/build \
  --parallel
```

## Tests

```bash
ctest \
  --test-dir sentinel-agent/build \
  --output-on-failure
```

The test suite covers Linux collectors, concurrency, scheduler backpressure, SQLite persistence, lifecycle signals and local DNS/TCP/HTTP diagnostics.

TLS and HTTPS are additionally tested in CI against an ephemeral local TLS server.

## Run interactively

```bash
./sentinel-agent/build/sentinel-agent
```

Pressing `Ctrl+C` triggers graceful shutdown.

For a bounded local run:

```bash
./sentinel-agent/build/sentinel-agent \
  --run-seconds 5
```

## systemd

The repository contains:

```text
packaging/systemd/nexusops-sentinel.service
```

The unit uses a dynamic service user and persistent systemd state directory.

The final Debian package will install the service unit automatically.

## Manual collector comparison

```bash
head -n 1 /proc/stat
grep -E 'MemTotal|MemAvailable' /proc/meminfo
df -B1 /
cat /proc/uptime
cat /proc/net/dev
cat /proc/self/status
```