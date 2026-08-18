# SentinelAgent

SentinelAgent is the Linux monitoring agent used by NexusOps.

The project is split into two main parts:

- `libsysprobe` - a C17 library for low-level Linux system data collection
- `sentinel-agent` - a C++20 application for scheduling, concurrency and agent orchestration

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

Process snapshots currently include:

- PID
- parent PID
- process name
- process state
- resident memory when available

Processes that disappear or become unreadable during a snapshot are skipped instead of causing the full collection to fail.

The collectors do not execute shell commands to obtain core metrics.

## Concurrency

The C++ runtime contains a fixed-size thread pool with a bounded work queue.

The scheduler submits periodic collector tasks to the pool.

The work queue is bounded so slow collectors cannot cause unlimited memory growth.

Submission is non-blocking:

- accepted work is queued for a worker
- a full queue returns `queue_full`
- a stopped pool rejects new work

The scheduler records rejected submissions as backpressure.

Missed periodic intervals are skipped instead of being submitted as a large catch-up burst.

Shutdown stops the scheduler first and then drains already accepted thread-pool work before joining the worker threads.

The current executable runs the scheduler for a short demonstration and exits. Long-running service lifecycle and signal handling are added in a later milestone.

## Requirements

- Linux
- GCC or Clang
- CMake 3.28+
- Ninja

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

The test suite covers the Linux collectors, bounded queue behavior, thread-pool shutdown, task exceptions, periodic scheduling and scheduler backpressure.

## Run

```bash
./sentinel-agent/build/sentinel-agent
```

The executable schedules the system, network and process collectors on the bounded thread pool.

A final runtime summary includes:

- worker count
- work queue capacity
- number of collector runs
- dropped scheduler submissions
- collector errors

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