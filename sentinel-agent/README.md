# SentinelAgent

SentinelAgent is the Linux monitoring agent used by NexusOps.

The project is split into two main parts:

- `libsysprobe` - a C17 library for low-level Linux system data collection
- `sentinel-agent` - a C++20 application that handles agent orchestration

## Current collectors

The current version collects:

- CPU usage from `/proc/stat`
- memory totals and available memory from `/proc/meminfo`
- filesystem capacity through `statvfs()`
- system uptime from `/proc/uptime`

CPU usage is calculated from the difference between two CPU counter snapshots.

The collectors do not execute shell commands to obtain core metrics.

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

## Run

```bash
./sentinel-agent/build/sentinel-agent
```

Example output:

```text
SentinelAgent using libsysprobe 0.2.0
cpu_usage_percent=3.42
memory_total_bytes=...
memory_available_bytes=...
memory_used_bytes=...
filesystem_total_bytes=...
filesystem_available_bytes=...
filesystem_used_bytes=...
uptime_seconds=...
```

Values depend on the Linux host at the time of collection.

## Manual comparison

The collected values can be compared with the host sources and standard Linux tools:

```bash
head -n 1 /proc/stat
grep -E 'MemTotal|MemAvailable' /proc/meminfo
df -B1 /
cat /proc/uptime
```

CPU percentages may differ slightly from other tools because sampling intervals are not necessarily identical.