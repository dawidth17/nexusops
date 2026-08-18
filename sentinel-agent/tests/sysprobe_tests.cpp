#include "nexusops/sysprobe.h"

#include <gtest/gtest.h>

TEST(SysprobeVersionTests, exposesExpectedMajorVersion)
{
    EXPECT_EQ(
        sysprobe_version_major(),
        0
    );
}

TEST(SysprobeVersionTests, exposesExpectedMinorVersion)
{
    EXPECT_EQ(
        sysprobe_version_minor(),
        3
    );
}

TEST(SysprobeVersionTests, exposesExpectedPatchVersion)
{
    EXPECT_EQ(
        sysprobe_version_patch(),
        0
    );
}

TEST(SysprobeStatusTests, successCodeIsZero)
{
    EXPECT_EQ(
        SYSPROBE_OK,
        0
    );
}

TEST(SysprobeStatusTests, errorCodesAreDistinct)
{
    EXPECT_NE(
        SYSPROBE_ERROR_INVALID_ARGUMENT,
        SYSPROBE_ERROR_IO
    );

    EXPECT_NE(
        SYSPROBE_ERROR_IO,
        SYSPROBE_ERROR_PARSE
    );

    EXPECT_NE(
        SYSPROBE_ERROR_PARSE,
        SYSPROBE_ERROR_BUFFER_TOO_SMALL
    );
}