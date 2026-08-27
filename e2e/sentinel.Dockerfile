FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install \
        --yes \
        --no-install-recommends \
        build-essential \
        ca-certificates \
        cmake \
        libgrpc++-dev \
        libprotobuf-dev \
        libsqlite3-dev \
        libssl-dev \
        ninja-build \
        pkg-config \
        protobuf-compiler \
        protobuf-compiler-grpc \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY contracts/protobuf ./contracts/protobuf

COPY sentinel-agent/CMakeLists.txt ./sentinel-agent/CMakeLists.txt
COPY sentinel-agent/README.md ./sentinel-agent/README.md
COPY sentinel-agent/app ./sentinel-agent/app
COPY sentinel-agent/include ./sentinel-agent/include
COPY sentinel-agent/src ./sentinel-agent/src
COPY sentinel-agent/packaging ./sentinel-agent/packaging

RUN cmake \
        -S sentinel-agent \
        -B sentinel-agent/build \
        -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DSENTINEL_BUILD_TESTS=OFF \
    && cmake \
        --build sentinel-agent/build \
        --parallel

ENTRYPOINT ["/workspace/sentinel-agent/build/sentinel-agent"]