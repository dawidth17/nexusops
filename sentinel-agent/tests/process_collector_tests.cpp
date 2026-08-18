#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"
#include "test_paths.h"

#include <gtest/gtest.h>

#include <algorithm>
#include <array>
#include <string>
#include <vector>

#include <unistd.h>

TEST(ProcessCollectorTests, readsProcessStatusFromFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_processes/100/status"
        );

    sysprobe_process_info process{};

    ASSERT_EQ(
        sysprobe_read_process_status_from_path(
            path.c_str(),
            &process
        ),
        SYSPROBE_OK
    );

    EXPECT_EQ(
        process.pid,
        100U
    );

    EXPECT_EQ(
        process.parent_pid,
        1U
    );

    EXPECT_EQ(
        process.state,
        'S'
    );

    EXPECT_STREQ(
        process.name,
        "sentinel-worker"
    );

    EXPECT_EQ(
        process.resident_memory_bytes,
        2097152U
    );
}

TEST(ProcessCollectorTests, acceptsProcessWithoutVmRss)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_processes/200/status"
        );

    sysprobe_process_info process{};

    ASSERT_EQ(
        sysprobe_read_process_status_from_path(
            path.c_str(),
            &process
        ),
        SYSPROBE_OK
    );

    EXPECT_EQ(
        process.pid,
        200U
    );

    EXPECT_EQ(
        process.resident_memory_bytes,
        0U
    );
}

TEST(ProcessCollectorTests, rejectsMalformedStatus)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_processes/400/status"
        );

    sysprobe_process_info process{};

    EXPECT_EQ(
        sysprobe_read_process_status_from_path(
            path.c_str(),
            &process
        ),
        SYSPROBE_ERROR_PARSE
    );
}

TEST(ProcessCollectorTests, enumeratesOnlyReadableValidProcesses)
{
    const std::string root =
        nexusops::test::fixturePath(
            "proc_processes"
        );

    std::array<
        sysprobe_process_info,
        4
    > processes{};

    size_t count = 0;

    ASSERT_EQ(
        sysprobe_read_processes_from_root(
            root.c_str(),
            processes.data(),
            processes.size(),
            &count
        ),
        SYSPROBE_OK
    );

    ASSERT_EQ(
        count,
        2U
    );

    EXPECT_EQ(
        processes[0].pid,
        100U
    );

    EXPECT_EQ(
        processes[1].pid,
        200U
    );
}

TEST(ProcessCollectorTests, reportsSmallBuffer)
{
    const std::string root =
        nexusops::test::fixturePath(
            "proc_processes"
        );

    std::array<
        sysprobe_process_info,
        1
    > processes{};

    size_t count = 0;

    EXPECT_EQ(
        sysprobe_read_processes_from_root(
            root.c_str(),
            processes.data(),
            processes.size(),
            &count
        ),
        SYSPROBE_ERROR_BUFFER_TOO_SMALL
    );

    EXPECT_EQ(
        count,
        2U
    );
}

TEST(ProcessCollectorTests, readsRealLinuxProcesses)
{
    size_t count = 0;

    ASSERT_EQ(
        sysprobe_read_processes(
            nullptr,
            0,
            &count
        ),
        SYSPROBE_OK
    );

    ASSERT_GT(
        count,
        0U
    );

    std::vector<
        sysprobe_process_info
    > processes(
        count + 32
    );

    size_t actualCount = 0;

    sysprobe_status status =
        sysprobe_read_processes(
            processes.data(),
            processes.size(),
            &actualCount
        );

    if (
        status ==
        SYSPROBE_ERROR_BUFFER_TOO_SMALL
    ) {
        processes.resize(
            actualCount + 32
        );

        status =
            sysprobe_read_processes(
                processes.data(),
                processes.size(),
                &actualCount
            );
    }

    ASSERT_EQ(
        status,
        SYSPROBE_OK
    );

    processes.resize(
        actualCount
    );

    pid_t currentPid =
        getpid();

    bool foundCurrentProcess =
        std::any_of(
            processes.begin(),
            processes.end(),
            [currentPid](
                const sysprobe_process_info &process
            ) {
                return process.pid ==
                    static_cast<uint32_t>(
                        currentPid
                    );
            }
        );

    EXPECT_TRUE(
        foundCurrentProcess
    );
}