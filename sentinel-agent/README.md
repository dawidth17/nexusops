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
AddressSanitizer + UndefinedBehaviorSanitizer
clang-tidy + cppcheck
Debian package build and installation smoke test
```

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
- dpkg tools for Debian packaging

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

## Manual collector comparison

```bash
head -n 1 /proc/stat
grep -E 'MemTotal|MemAvailable' /proc/meminfo
df -B1 /
cat /proc/uptime
cat /proc/net/dev
cat /proc/self/status
```