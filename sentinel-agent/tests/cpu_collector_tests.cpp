#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"
#include "test_paths.h"

#include <gtest/gtest.h>

TEST(CpuCollectorTests, readsCpuTimesFromFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_stat.txt"
        );

    sysprobe_cpu_times times{};

    ASSERT_EQ(
        sysprobe_read_cpu_times_from_path(
            path.c_str(),
            &times
        ),
        SYSPROBE_OK
    );

    EXPECT_EQ(times.user, 100U);
    EXPECT_EQ(times.nice, 20U);
    EXPECT_EQ(times.system, 30U);
    EXPECT_EQ(times.idle, 400U);
    EXPECT_EQ(times.iowait, 50U);
    EXPECT_EQ(times.irq, 5U);
    EXPECT_EQ(times.softirq, 10U);
    EXPECT_EQ(times.steal, 0U);
}

TEST(CpuCollectorTests, calculatesUsageFromTwoSnapshots)
{
    const std::string firstPath =
        nexusops::test::fixturePath(
            "proc_stat.txt"
        );

    const std::string secondPath =
        nexusops::test::fixturePath(
            "proc_stat_later.txt"
        );

    sysprobe_cpu_times first{};
    sysprobe_cpu_times second{};

    ASSERT_EQ(
        sysprobe_read_cpu_times_from_path(
            firstPath.c_str(),
            &first
        ),
        SYSPROBE_OK
    );

    ASSERT_EQ(
        sysprobe_read_cpu_times_from_path(
            secondPath.c_str(),
            &second
        ),
        SYSPROBE_OK
    );

    double usagePercent = 0.0;

    ASSERT_EQ(
        sysprobe_calculate_cpu_usage_percent(
            &first,
            &second,
            &usagePercent
        ),
        SYSPROBE_OK
    );

    EXPECT_NEAR(
        usagePercent,
        56.25,
        0.001
    );
}

TEST(CpuCollectorTests, rejectsMalformedFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_stat_invalid.txt"
        );

    sysprobe_cpu_times times{};

    EXPECT_EQ(
        sysprobe_read_cpu_times_from_path(
            path.c_str(),
            &times
        ),
        SYSPROBE_ERROR_PARSE
    );
}

TEST(CpuCollectorTests, rejectsNullOutput)
{
    EXPECT_EQ(
        sysprobe_read_cpu_times(nullptr),
        SYSPROBE_ERROR_INVALID_ARGUMENT
    );
}

TEST(CpuCollectorTests, readsRealLinuxProcStat)
{
    sysprobe_cpu_times times{};

    ASSERT_EQ(
        sysprobe_read_cpu_times(&times),
        SYSPROBE_OK
    );

    EXPECT_GT(
        times.user +
            times.nice +
            times.system +
            times.idle,
        0U
    );
}