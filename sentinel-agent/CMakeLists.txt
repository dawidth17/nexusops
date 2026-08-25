cmake_minimum_required(VERSION 3.28)

project(
    SentinelAgent
    VERSION 0.9.0
    DESCRIPTION "NexusOps Linux monitoring agent"
    LANGUAGES C CXX
)

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

option(
    SENTINEL_BUILD_TESTS
    "Build SentinelAgent tests"
    ON
)

option(
    SENTINEL_ENABLE_SANITIZERS
    "Enable AddressSanitizer and UndefinedBehaviorSanitizer"
    OFF
)

if(SENTINEL_ENABLE_SANITIZERS)
    if(
        NOT CMAKE_C_COMPILER_ID MATCHES "GNU|Clang"
        OR
        NOT CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang"
    )
        message(
            FATAL_ERROR
            "SentinelAgent sanitizers require GCC or Clang"
        )
    endif()
endif()

function(sentinel_apply_target_options target)
    target_compile_options(
        ${target}
        PRIVATE
            -Wall
            -Wextra
            -Wpedantic
    )

    if(SENTINEL_ENABLE_SANITIZERS)
        target_compile_options(
            ${target}
            PRIVATE
                -fsanitize=address,undefined
                -fno-omit-frame-pointer
                -fno-sanitize-recover=all
        )

        get_target_property(
            target_type
            ${target}
            TYPE
        )

        if(
            target_type STREQUAL "EXECUTABLE"
            OR
            target_type STREQUAL "SHARED_LIBRARY"
            OR
            target_type STREQUAL "MODULE_LIBRARY"
        )
            target_link_options(
                ${target}
                PRIVATE
                    -fsanitize=address,undefined
                    -fno-omit-frame-pointer
            )
        endif()
    endif()
endfunction()

find_package(
    Threads
    REQUIRED
)

find_package(
    SQLite3
    REQUIRED
)

find_package(
    OpenSSL
    REQUIRED
)

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
    NEXUSOPS_CONTRACT_ROOT
    "${CMAKE_CURRENT_SOURCE_DIR}/../contracts/protobuf"
)

set(
    TELEMETRY_PROTO
    "${NEXUSOPS_CONTRACT_ROOT}/nexusops/telemetry/v1/telemetry.proto"
)

if(NOT EXISTS "${TELEMETRY_PROTO}")
    message(
        FATAL_ERROR
        "NexusOps telemetry contract was not found"
    )
endif()

set(
    TELEMETRY_GENERATED_ROOT
    "${CMAKE_CURRENT_BINARY_DIR}/generated"
)

set(
    TELEMETRY_GENERATED_DIRECTORY
    "${TELEMETRY_GENERATED_ROOT}/nexusops/telemetry/v1"
)

set(
    TELEMETRY_PROTO_SOURCE
    "${TELEMETRY_GENERATED_DIRECTORY}/telemetry.pb.cc"
)

set(
    TELEMETRY_PROTO_HEADER
    "${TELEMETRY_GENERATED_DIRECTORY}/telemetry.pb.h"
)

set(
    TELEMETRY_GRPC_SOURCE
    "${TELEMETRY_GENERATED_DIRECTORY}/telemetry.grpc.pb.cc"
)

set(
    TELEMETRY_GRPC_HEADER
    "${TELEMETRY_GENERATED_DIRECTORY}/telemetry.grpc.pb.h"
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
        "${TELEMETRY_GENERATED_DIRECTORY}"

    COMMAND
        "${Protobuf_PROTOC_EXECUTABLE}"
        "--proto_path=${NEXUSOPS_CONTRACT_ROOT}"
        "--proto_path=${PROTOBUF_WKT_ROOT}"
        "--cpp_out=${TELEMETRY_GENERATED_ROOT}"
        "--grpc_out=${TELEMETRY_GENERATED_ROOT}"
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
        "${TELEMETRY_GENERATED_ROOT}"
)

target_link_libraries(
    nexusops_telemetry_contract
    PUBLIC
        protobuf::libprotobuf
        PkgConfig::GRPCPP
)

add_library(
    sysprobe
    STATIC
    src/sysprobe.c
    src/cpu_collector.c
    src/memory_collector.c
    src/filesystem_collector.c
    src/uptime_collector.c
    src/network_collector.c
    src/process_collector.c
)

target_include_directories(
    sysprobe
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

sentinel_apply_target_options(
    sysprobe
)

add_library(
    sentinel_runtime
    STATIC
    src/bounded_thread_pool.cpp
    src/scheduler.cpp
    src/signal_waiter.cpp
    src/network_diagnostics.cpp
    src/diagnostic_cli.cpp
)

target_include_directories(
    sentinel_runtime
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(
    sentinel_runtime
    PUBLIC
        Threads::Threads
        OpenSSL::SSL
        OpenSSL::Crypto
)

sentinel_apply_target_options(
    sentinel_runtime
)

add_library(
    sentinel_storage
    STATIC
    src/sqlite_spool.cpp
)

target_include_directories(
    sentinel_storage
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(
    sentinel_storage
    PUBLIC
        SQLite::SQLite3
)

sentinel_apply_target_options(
    sentinel_storage
)

add_library(
    sentinel_telemetry_codec
    STATIC
    src/telemetry_codec.cpp
)

target_include_directories(
    sentinel_telemetry_codec
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(
    sentinel_telemetry_codec
    PUBLIC
        sysprobe
        nexusops_telemetry_contract
)

sentinel_apply_target_options(
    sentinel_telemetry_codec
)

add_library(
    sentinel_grpc_transport
    STATIC
    src/grpc_telemetry_client.cpp
)

target_include_directories(
    sentinel_grpc_transport
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(
    sentinel_grpc_transport
    PUBLIC
        sentinel_storage
        nexusops_telemetry_contract
        PkgConfig::GRPCPP
)

sentinel_apply_target_options(
    sentinel_grpc_transport
)

add_executable(
    sentinel-agent
    app/main.cpp
)

target_link_libraries(
    sentinel-agent
    PRIVATE
        sysprobe
        sentinel_runtime
        sentinel_storage
        sentinel_telemetry_codec
        sentinel_grpc_transport
)

target_compile_definitions(
    sentinel-agent
    PRIVATE
        SENTINEL_AGENT_VERSION="${PROJECT_VERSION}"
)

sentinel_apply_target_options(
    sentinel-agent
)

if(SENTINEL_BUILD_TESTS)
    include(CTest)
    include(FetchContent)

    FetchContent_Declare(
        googletest
        GIT_REPOSITORY
            https://github.com/google/googletest.git
        GIT_TAG
            v1.18.0
        GIT_SHALLOW
            TRUE
    )

    FetchContent_MakeAvailable(googletest)

    add_executable(
        sysprobe_tests
        tests/sysprobe_tests.cpp
        tests/cpu_collector_tests.cpp
        tests/memory_collector_tests.cpp
        tests/filesystem_collector_tests.cpp
        tests/uptime_collector_tests.cpp
        tests/network_collector_tests.cpp
        tests/process_collector_tests.cpp
    )

    target_include_directories(
        sysprobe_tests
        PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/src
            ${CMAKE_CURRENT_SOURCE_DIR}/tests
    )

    target_compile_definitions(
        sysprobe_tests
        PRIVATE
            SYSPROBE_TEST_FIXTURE_DIR="${CMAKE_CURRENT_SOURCE_DIR}/tests/fixtures"
    )

    target_link_libraries(
        sysprobe_tests
        PRIVATE
            sysprobe
            GTest::gtest_main
    )

    sentinel_apply_target_options(
        sysprobe_tests
    )

    add_executable(
        sentinel_runtime_tests
        tests/bounded_thread_pool_tests.cpp
        tests/scheduler_tests.cpp
        tests/signal_waiter_tests.cpp
        tests/network_diagnostics_tests.cpp
    )

    target_link_libraries(
        sentinel_runtime_tests
        PRIVATE
            sentinel_runtime
            GTest::gtest_main
    )

    sentinel_apply_target_options(
        sentinel_runtime_tests
    )

    add_executable(
        sentinel_storage_tests
        tests/sqlite_spool_tests.cpp
    )

    target_link_libraries(
        sentinel_storage_tests
        PRIVATE
            sentinel_storage
            Threads::Threads
            GTest::gtest_main
    )

    sentinel_apply_target_options(
        sentinel_storage_tests
    )

    add_executable(
        sentinel_telemetry_codec_tests
        tests/telemetry_codec_tests.cpp
    )

    target_link_libraries(
        sentinel_telemetry_codec_tests
        PRIVATE
            sentinel_telemetry_codec
            sentinel_storage
            GTest::gtest_main
    )

    sentinel_apply_target_options(
        sentinel_telemetry_codec_tests
    )

    add_executable(
        sentinel_grpc_transport_tests
        tests/grpc_telemetry_client_tests.cpp
    )

    target_link_libraries(
        sentinel_grpc_transport_tests
        PRIVATE
            sentinel_grpc_transport
            sentinel_storage
            nexusops_telemetry_contract
            GTest::gtest_main
    )

    sentinel_apply_target_options(
        sentinel_grpc_transport_tests
    )

    include(GoogleTest)

    gtest_discover_tests(
        sysprobe_tests
    )

    gtest_discover_tests(
        sentinel_runtime_tests
    )

    gtest_discover_tests(
        sentinel_storage_tests
    )

    gtest_discover_tests(
        sentinel_telemetry_codec_tests
    )

    gtest_discover_tests(
        sentinel_grpc_transport_tests
    )
endif()

install(
    PROGRAMS
        "$<TARGET_FILE:sentinel-agent>"
    DESTINATION
        "usr/bin"
    RENAME
        "nexusops-sentinel-agent"
)

install(
    FILES
        packaging/systemd/nexusops-sentinel.service
    DESTINATION
        "usr/lib/systemd/system"
)

install(
    FILES
        packaging/config/sentinel.conf
    DESTINATION
        "etc/nexusops-sentinel"
)

install(
    FILES
        README.md
    DESTINATION
        "usr/share/doc/nexusops-sentinel-agent"
)

set(
    CPACK_GENERATOR
    "DEB"
)

set(
    CPACK_PACKAGE_NAME
    "nexusops-sentinel-agent"
)

set(
    CPACK_PACKAGE_VENDOR
    "NexusOps"
)

set(
    CPACK_PACKAGE_CONTACT
    "NexusOps Maintainer <dawidth17@users.noreply.github.com>"
)

set(
    CPACK_PACKAGE_DESCRIPTION_SUMMARY
    "Linux monitoring and diagnostics agent for NexusOps"
)

set(
    CPACK_PACKAGE_VERSION
    "${PROJECT_VERSION}"
)

set(
    CPACK_PACKAGING_INSTALL_PREFIX
    "/"
)

set(
    CPACK_PACKAGE_DIRECTORY
    "${CMAKE_BINARY_DIR}/packages"
)

set(
    CPACK_DEBIAN_PACKAGE_NAME
    "nexusops-sentinel-agent"
)

set(
    CPACK_DEBIAN_FILE_NAME
    "DEB-DEFAULT"
)

set(
    CPACK_DEBIAN_PACKAGE_RELEASE
    "1"
)

set(
    CPACK_DEBIAN_PACKAGE_SECTION
    "admin"
)

set(
    CPACK_DEBIAN_PACKAGE_PRIORITY
    "optional"
)

set(
    CPACK_DEBIAN_PACKAGE_MAINTAINER
    "${CPACK_PACKAGE_CONTACT}"
)

set(
    CPACK_DEBIAN_PACKAGE_HOMEPAGE
    "https://github.com/dawidth17/nexusops"
)

set(
    CPACK_DEBIAN_PACKAGE_DEPENDS
    "systemd"
)

set(
    CPACK_DEBIAN_PACKAGE_SHLIBDEPS
    ON
)

set(
    CPACK_DEBIAN_PACKAGE_CONTROL_EXTRA
    "${CMAKE_CURRENT_SOURCE_DIR}/packaging/debian/conffiles"
    "${CMAKE_CURRENT_SOURCE_DIR}/packaging/debian/postinst"
    "${CMAKE_CURRENT_SOURCE_DIR}/packaging/debian/prerm"
    "${CMAKE_CURRENT_SOURCE_DIR}/packaging/debian/postrm"
)

set(
    CPACK_DEBIAN_PACKAGE_CONTROL_STRICT_PERMISSION
    TRUE
)

include(CPack)