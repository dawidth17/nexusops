# SentinelAgent

SentinelAgent is the Linux monitoring agent used by NexusOps.

The project is split into three main parts:

- `libsysprobe` - a C17 library for low-level Linux system data collection
- `sentinel_runtime` - C++20 scheduling and concurrency
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

CPU usage is calculated from the difference between two CPU counter snapshots.

Processes that disappear or become unreadable during a snapshot are skipped instead of causing the full collection to fail.

The collectors do not execute shell commands to obtain core metrics.

## Concurrency

The C++ runtime contains a fixed-size thread pool with a bounded work queue.

The scheduler submits periodic collector tasks to the pool.

The work queue is bounded so slow collectors cannot cause unlimited in-memory growth.

Submission is non-blocking:

- accepted work is queued for a worker
- a full queue returns `queue_full`
- a stopped pool rejects new work

The scheduler records rejected submissions as backpressure.

Missed periodic intervals are skipped instead of being submitted as a large catch-up burst.

Shutdown stops the scheduler first and then drains already accepted thread-pool work before joining the worker threads.

## SQLite durable spool

Collected telemetry can be stored in a local SQLite spool.

Each stored record contains:

- an increasing sequence number
- UTC capture time
- telemetry kind
- an opaque payload

Records are read in sequence order.

Acknowledgement removes all records up to and including an acknowledged sequence number.

The spool has a fixed record capacity and a maximum payload size so offline buffering cannot grow without an explicit bound.

The SQLite connection is protected by a mutex so collector tasks can write through the same spool safely.

The current schema is versioned through SQLite `user_version`.

Schema version 1 creates the `telemetry_spool` table.

SQLite uses WAL journaling with full synchronous durability for the local spool.

Until the real telemetry transport is implemented, the executable uses the spool as a local telemetry sink.

The demo closes and reopens the database to verify that telemetry remains available before acknowledgement.

The temporary demo database is removed when the executable exits.

## Requirements

- Linux
- GCC or Clang
- CMake 3.28+
- Ninja
- SQLite 3 development library

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

The test suite covers:

- Linux collectors
- bounded queue behavior
- thread-pool shutdown
- task exceptions
- periodic scheduling
- scheduler backpressure
- SQLite schema creation
- ordered spool reads
- spool capacity
- acknowledgement
- persistence across reopen
- concurrent spool writes

## Run

```bash
./sentinel-agent/build/sentinel-agent
```

The executable schedules the system, network and process collectors on the bounded thread pool and persists the generated telemetry into SQLite.

The runtime summary includes collector counts, scheduler backpressure, spool records and storage errors.

A second summary verifies that the same records can be read after the database is closed and reopened.

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

CPU percentages may differ slightly from other tools because sampling intervals are not necessarily identical.