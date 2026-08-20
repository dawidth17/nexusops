cmake_minimum_required(VERSION 3.28)

project(
    SentinelAgent
    VERSION 0.5.0
    DESCRIPTION "NexusOps Linux monitoring agent"
    LANGUAGES C CXX
)

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(
    SENTINEL_BUILD_TESTS
    "Build SentinelAgent tests"
    ON
)

find_package(
    Threads
    REQUIRED
)

find_package(
    SQLite3
    REQUIRED
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

target_compile_options(
    sysprobe
    PRIVATE
        -Wall
        -Wextra
        -Wpedantic
)

add_library(
    sentinel_runtime
    STATIC
    src/bounded_thread_pool.cpp
    src/scheduler.cpp
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
)

target_compile_options(
    sentinel_runtime
    PRIVATE
        -Wall
        -Wextra
        -Wpedantic
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

target_compile_options(
    sentinel_storage
    PRIVATE
        -Wall
        -Wextra
        -Wpedantic
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
)

target_compile_definitions(
    sentinel-agent
    PRIVATE
        SENTINEL_AGENT_VERSION="${PROJECT_VERSION}"
)

target_compile_options(
    sentinel-agent
    PRIVATE
        -Wall
        -Wextra
        -Wpedantic
)

if(SENTINEL_BUILD_TESTS)
    include(CTest)
    include(FetchContent)

    FetchContent_Declare(
        googletest
        GIT_REPOSITORY https://github.com/google/googletest.git
        GIT_TAG v1.18.0
        GIT_SHALLOW TRUE
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

    target_compile_options(
        sysprobe_tests
        PRIVATE
            -Wall
            -Wextra
            -Wpedantic
    )

    add_executable(
        sentinel_runtime_tests
        tests/bounded_thread_pool_tests.cpp
        tests/scheduler_tests.cpp
    )

    target_link_libraries(
        sentinel_runtime_tests
        PRIVATE
            sentinel_runtime
            GTest::gtest_main
    )

    target_compile_options(
        sentinel_runtime_tests
        PRIVATE
            -Wall
            -Wextra
            -Wpedantic
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

    target_compile_options(
        sentinel_storage_tests
        PRIVATE
            -Wall
            -Wextra
            -Wpedantic
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
endif()