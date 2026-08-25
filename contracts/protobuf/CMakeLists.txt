cmake_minimum_required(VERSION 3.28)

project(
    NexusOpsProtobufContracts
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(
    Protobuf
    REQUIRED
)

find_package(
    PkgConfig
    REQUIRED
)

pkg_check_modules(
    GRPCPP
    REQUIRED
    IMPORTED_TARGET
    grpc++
)

find_program(
    GRPC_CPP_PLUGIN
    grpc_cpp_plugin
    REQUIRED
)

find_path(
    PROTOBUF_WKT_ROOT
    google/protobuf/timestamp.proto
    PATHS
        ${Protobuf_INCLUDE_DIRS}
    REQUIRED
)

set(
    TELEMETRY_PROTO
    "${CMAKE_CURRENT_SOURCE_DIR}/nexusops/telemetry/v1/telemetry.proto"
)

set(
    GENERATED_ROOT
    "${CMAKE_CURRENT_BINARY_DIR}/generated"
)

set(
    GENERATED_DIRECTORY
    "${GENERATED_ROOT}/nexusops/telemetry/v1"
)

set(
    TELEMETRY_PROTO_SOURCE
    "${GENERATED_DIRECTORY}/telemetry.pb.cc"
)

set(
    TELEMETRY_PROTO_HEADER
    "${GENERATED_DIRECTORY}/telemetry.pb.h"
)

set(
    TELEMETRY_GRPC_SOURCE
    "${GENERATED_DIRECTORY}/telemetry.grpc.pb.cc"
)

set(
    TELEMETRY_GRPC_HEADER
    "${GENERATED_DIRECTORY}/telemetry.grpc.pb.h"
)

add_custom_command(
    OUTPUT
        "${TELEMETRY_PROTO_SOURCE}"
        "${TELEMETRY_PROTO_HEADER}"
        "${TELEMETRY_GRPC_SOURCE}"
        "${TELEMETRY_GRPC_HEADER}"

    COMMAND
        ${CMAKE_COMMAND}
        -E
        make_directory
        "${GENERATED_DIRECTORY}"

    COMMAND
        "${Protobuf_PROTOC_EXECUTABLE}"
        "--proto_path=${CMAKE_CURRENT_SOURCE_DIR}"
        "--proto_path=${PROTOBUF_WKT_ROOT}"
        "--cpp_out=${GENERATED_ROOT}"
        "--grpc_out=${GENERATED_ROOT}"
        "--plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN}"
        "${TELEMETRY_PROTO}"

    DEPENDS
        "${TELEMETRY_PROTO}"

    VERBATIM
)

add_library(
    nexusops_telemetry_contract
    STATIC
    "${TELEMETRY_PROTO_SOURCE}"
    "${TELEMETRY_GRPC_SOURCE}"
)

target_include_directories(
    nexusops_telemetry_contract
    PUBLIC
        "${GENERATED_ROOT}"
)

target_link_libraries(
    nexusops_telemetry_contract
    PUBLIC
        protobuf::libprotobuf
        PkgConfig::GRPCPP
)

add_executable(
    telemetry_contract_smoke
    tests/cpp_contract_smoke.cpp
)

target_link_libraries(
    telemetry_contract_smoke
    PRIVATE
        nexusops_telemetry_contract
)

enable_testing()

add_test(
    NAME
        telemetry_contract_smoke
    COMMAND
        telemetry_contract_smoke
)