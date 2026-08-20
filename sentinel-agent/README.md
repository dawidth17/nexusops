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

A custom CA certificate can be supplied as the final argument.

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

The CI pipeline adds three independent quality gates:

```text
regular build and tests
AddressSanitizer + UndefinedBehaviorSanitizer
clang-tidy + cppcheck
```

AddressSanitizer is used to detect memory-safety errors.

UndefinedBehaviorSanitizer detects undefined runtime behavior.

Static analysis is performed against the project compilation database.

`clang-tidy` runs analyzer, bug-prone, performance and portability checks.

`cppcheck` runs warning, performance and portability analysis.

Static-analysis findings configured by the project fail the CI quality gate.

## CMake presets

The repository provides shared CMake presets for development and sanitizer builds.

From the `sentinel-agent` directory:

### Development build

```bash
cmake --preset dev
cmake --build --preset dev
ctest --preset dev
```

### Sanitizer build

```bash
cmake --preset sanitizers
cmake --build --preset sanitizers
ctest --preset sanitizers
```

The sanitizer test preset sets the required ASan and UBSan runtime options.

## Local static analysis

Install the tools if needed:

```bash
sudo apt-get install \
  -y \
  clang-tidy \
  cppcheck
```

Create the compilation database from the repository root:

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
- clang-tidy for static analysis
- cppcheck for static analysis

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