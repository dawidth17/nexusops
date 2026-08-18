# SentinelAgent

SentinelAgent is the Linux monitoring agent used by NexusOps.

The project is split into two main parts:

- `libsysprobe` - a C17 library for low-level Linux system data collection
- `sentinel-agent` - a C++20 application that handles agent orchestration

## Requirements

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

Expected output:

```text
SentinelAgent using libsysprobe 0.1.0
```