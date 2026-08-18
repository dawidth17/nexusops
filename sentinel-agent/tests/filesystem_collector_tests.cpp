#include "nexusops/sysprobe.h"

#include <gtest/gtest.h>

TEST(FilesystemCollectorTests, readsRootFilesystem)
{
    sysprobe_filesystem_info filesystem{};

    ASSERT_EQ(
        sysprobe_read_filesystem_info(
            "/",
            &filesystem
        ),
        SYSPROBE_OK
    );

    EXPECT_GT(
        filesystem.total_bytes,
        0U
    );

    EXPECT_LE(
        filesystem.free_bytes,
        filesystem.total_bytes
    );

    EXPECT_LE(
        filesystem.available_bytes,
        filesystem.total_bytes
    );

    EXPECT_EQ(
        filesystem.used_bytes,
        filesystem.total_bytes -
            filesystem.free_bytes
    );
}

TEST(FilesystemCollectorTests, rejectsMissingPath)
{
    sysprobe_filesystem_info filesystem{};

    EXPECT_EQ(
        sysprobe_read_filesystem_info(
            "/nexusops/path/that/does/not/exist",
            &filesystem
        ),
        SYSPROBE_ERROR_IO
    );
}

TEST(FilesystemCollectorTests, rejectsNullArguments)
{
    sysprobe_filesystem_info filesystem{};

    EXPECT_EQ(
        sysprobe_read_filesystem_info(
            nullptr,
            &filesystem
        ),
        SYSPROBE_ERROR_INVALID_ARGUMENT
    );

    EXPECT_EQ(
        sysprobe_read_filesystem_info(
            "/",
            nullptr
        ),
        SYSPROBE_ERROR_INVALID_ARGUMENT
    );
}