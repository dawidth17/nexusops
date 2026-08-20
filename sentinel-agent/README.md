# SentinelAgent

SentinelAgent is the Linux monitoring agent used by NexusOps.

The project is split into three main parts:

- `libsysprobe` - a C17 library for low-level Linux system data collection
- `sentinel_runtime` - C++20 scheduling, concurrency and lifecycle handling
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

Each record contains:

- an increasing sequence number
- UTC capture time
- telemetry kind
- telemetry payload

The SQLite schema is versioned.

The spool has bounded record and payload limits.

The connection is protected by a mutex so collector workers can safely share the spool.

Telemetry remains stored across agent restarts until a later transport milestone acknowledges it.

## Lifecycle and signals

SentinelAgent can now run as a long-lived Linux service.

Lifecycle signals are blocked before worker threads are created and consumed synchronously by the main control thread.

The agent handles:

- `SIGTERM` as a graceful shutdown request
- `SIGINT` as a graceful shutdown request
- `SIGHUP` as a reload request

A reload request currently keeps the agent running and records the lifecycle event. Reloadable configuration will be connected when configuration state requires it.

Graceful shutdown happens in this order:

```text
shutdown signal
    |
    v
stop scheduler
    |
    v
drain bounded thread pool
    |
    v
finish SQLite operations
    |
    v
close database and exit
```

No complex C++ or SQLite work is executed from an asynchronous signal handler.

## Local state

The spool path is resolved in this order:

```text
NEXUSOPS_SENTINEL_SPOOL_PATH
XDG_STATE_HOME
HOME/.local/state/nexusops-sentinel
/tmp fallback
```

The systemd service explicitly stores the spool at:

```text
/var/lib/nexusops-sentinel/telemetry.db
```

## Requirements

- Linux
- GCC or Clang
- CMake 3.28+
- Ninja
- SQLite 3 development library
- pthreads

## Configure

From the repository root:

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

The test suite covers collectors, concurrency, scheduler backpressure, SQLite persistence and lifecycle signal handling.

## Run interactively

Without a runtime limit, the agent continues until it receives a shutdown signal:

```bash
./sentinel-agent/build/sentinel-agent
```

Pressing `Ctrl+C` sends `SIGINT` and triggers graceful shutdown.

For CI or a short local demonstration:

```bash
./sentinel-agent/build/sentinel-agent \
  --run-seconds 5
```

## systemd

The repository contains:

```text
packaging/systemd/nexusops-sentinel.service
```

The unit uses a dynamic service user and a persistent systemd state directory.

The final Debian package will install the unit automatically.

For a development smoke test, the built binary and unit can be installed manually.

## Manual collector comparison

The collected values can be compared with Linux system sources:

```bash
head -n 1 /proc/stat
grep -E 'MemTotal|MemAvailable' /proc/meminfo
df -B1 /
cat /proc/uptime
cat /proc/net/dev
cat /proc/self/status
```