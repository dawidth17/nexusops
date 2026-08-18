#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"
#include "test_paths.h"

#include <gtest/gtest.h>

#include <array>
#include <string>
#include <vector>

TEST(NetworkCollectorTests, countsInterfacesFromFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_net_dev.txt"
        );

    size_t count = 0;

    ASSERT_EQ(
        sysprobe_read_network_counters_from_path(
            path.c_str(),
            nullptr,
            0,
            &count
        ),
        SYSPROBE_OK
    );

    EXPECT_EQ(
        count,
        2U
    );
}

TEST(NetworkCollectorTests, readsCountersFromFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_net_dev.txt"
        );

    std::array<
        sysprobe_network_interface,
        2
    > interfaces{};

    size_t count = 0;

    ASSERT_EQ(
        sysprobe_read_network_counters_from_path(
            path.c_str(),
            interfaces.data(),
            interfaces.size(),
            &count
        ),
        SYSPROBE_OK
    );

    ASSERT_EQ(
        count,
        2U
    );

    EXPECT_STREQ(
        interfaces[0].name,
        "lo"
    );

    EXPECT_EQ(
        interfaces[0].rx_bytes,
        1000U
    );

    EXPECT_EQ(
        interfaces[0].rx_packets,
        10U
    );

    EXPECT_EQ(
        interfaces[0].rx_errors,
        1U
    );

    EXPECT_EQ(
        interfaces[0].rx_dropped,
        2U
    );

    EXPECT_EQ(
        interfaces[0].tx_bytes,
        2000U
    );

    EXPECT_EQ(
        interfaces[0].tx_packets,
        20U
    );

    EXPECT_STREQ(
        interfaces[1].name,
        "eth0"
    );

    EXPECT_EQ(
        interfaces[1].rx_bytes,
        3000U
    );

    EXPECT_EQ(
        interfaces[1].tx_bytes,
        4000U
    );
}

TEST(NetworkCollectorTests, reportsSmallBuffer)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_net_dev.txt"
        );

    std::array<
        sysprobe_network_interface,
        1
    > interfaces{};

    size_t count = 0;

    EXPECT_EQ(
        sysprobe_read_network_counters_from_path(
            path.c_str(),
            interfaces.data(),
            interfaces.size(),
            &count
        ),
        SYSPROBE_ERROR_BUFFER_TOO_SMALL
    );

    EXPECT_EQ(
        count,
        2U
    );
}

TEST(NetworkCollectorTests, rejectsMalformedFixture)
{
    const std::string path =
        nexusops::test::fixturePath(
            "proc_net_dev_invalid.txt"
        );

    size_t count = 0;

    EXPECT_EQ(
        sysprobe_read_network_counters_from_path(
            path.c_str(),
            nullptr,
            0,
            &count
        ),
        SYSPROBE_ERROR_PARSE
    );
}

TEST(NetworkCollectorTests, readsRealLinuxInterfaces)
{
    size_t count = 0;

    ASSERT_EQ(
        sysprobe_read_network_interfaces(
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
        sysprobe_network_interface
    > interfaces(
        count + 4
    );

    size_t actualCount = 0;

    ASSERT_EQ(
        sysprobe_read_network_interfaces(
            interfaces.data(),
            interfaces.size(),
            &actualCount
        ),
        SYSPROBE_OK
    );

    ASSERT_GT(
        actualCount,
        0U
    );

    bool foundAddress = false;

    for (
        size_t index = 0;
        index < actualCount;
        ++index
    ) {
        EXPECT_NE(
            interfaces[index].name[0],
            '\0'
        );

        if (
            interfaces[index].ipv4_address[0] !=
                '\0' ||
            interfaces[index].ipv6_address[0] !=
                '\0'
        ) {
            foundAddress = true;
        }
    }

    EXPECT_TRUE(
        foundAddress
    );
}