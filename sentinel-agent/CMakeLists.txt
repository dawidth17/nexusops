cmake_minimum_required(VERSION 3.28)

project(
    SentinelAgent
    VERSION 0.1.0
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

add_library(
    sysprobe
    STATIC
    src/sysprobe.c
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

add_executable(
    sentinel-agent
    app/main.cpp
)

target_link_libraries(
    sentinel-agent
    PRIVATE
        sysprobe
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

    include(GoogleTest)

    gtest_discover_tests(
        sysprobe_tests
    )
endif()