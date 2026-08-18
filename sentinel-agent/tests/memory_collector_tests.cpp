#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"
#include "test_paths.h"

#include <gtest/gtest.h>

TEST(MemoryCollectorTests, readsMemoryFromFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_meminfo.txt"
        );

    sysprobe_memory_info memory{};

    ASSERT_EQ(
        sysprobe_read_memory_info_from_path(
            path.c_str(),
            &memory
        ),
        SYSPROBE_OK
    );

    EXPECT_EQ(
        memory.total_bytes,
        16777216000ULL
    );

    EXPECT_EQ(
        memory.available_bytes,
        8388608000ULL
    );

    EXPECT_EQ(
        memory.used_bytes,
        8388608000ULL
    );
}

TEST(MemoryCollectorTests, rejectsMissingRequiredField)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_meminfo_invalid.txt"
        );

    sysprobe_memory_info memory{};

    EXPECT_EQ(
        sysprobe_read_memory_info_from_path(
            path.c_str(),
            &memory
        ),
        SYSPROBE_ERROR_PARSE
    );
}

TEST(MemoryCollectorTests, rejectsNullOutput)
{
    EXPECT_EQ(
        sysprobe_read_memory_info(nullptr),
        SYSPROBE_ERROR_INVALID_ARGUMENT
    );
}

TEST(MemoryCollectorTests, readsRealLinuxMeminfo)
{
    sysprobe_memory_info memory{};

    ASSERT_EQ(
        sysprobe_read_memory_info(&memory),
        SYSPROBE_OK
    );

    EXPECT_GT(
        memory.total_bytes,
        0U
    );

    EXPECT_LE(
        memory.available_bytes,
        memory.total_bytes
    );

    EXPECT_EQ(
        memory.used_bytes,
        memory.total_bytes -
            memory.available_bytes
    );
}