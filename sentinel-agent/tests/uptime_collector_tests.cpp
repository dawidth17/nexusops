#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"
#include "test_paths.h"

#include <gtest/gtest.h>

TEST(UptimeCollectorTests, readsUptimeFromFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_uptime.txt"
        );

    sysprobe_uptime_info uptime{};

    ASSERT_EQ(
        sysprobe_read_uptime_from_path(
            path.c_str(),
            &uptime
        ),
        SYSPROBE_OK
    );

    EXPECT_NEAR(
        uptime.uptime_seconds,
        12345.67,
        0.001
    );
}

TEST(UptimeCollectorTests, rejectsMalformedFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_uptime_invalid.txt"
        );

    sysprobe_uptime_info uptime{};

    EXPECT_EQ(
        sysprobe_read_uptime_from_path(
            path.c_str(),
            &uptime
        ),
        SYSPROBE_ERROR_PARSE
    );
}

TEST(UptimeCollectorTests, rejectsNullOutput)
{
    EXPECT_EQ(
        sysprobe_read_uptime(nullptr),
        SYSPROBE_ERROR_INVALID_ARGUMENT
    );
}

TEST(UptimeCollectorTests, readsRealLinuxUptime)
{
    sysprobe_uptime_info uptime{};

    ASSERT_EQ(
        sysprobe_read_uptime(&uptime),
        SYSPROBE_OK
    );

    EXPECT_GT(
        uptime.uptime_seconds,
        0.0
    );
}