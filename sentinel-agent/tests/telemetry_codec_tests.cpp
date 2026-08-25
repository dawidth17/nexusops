#include "nexusops/agent/sqlite_spool.h"
#include "nexusops/agent/telemetry_codec.h"
#include "nexusops/telemetry/v1/telemetry.pb.h"

#include <gtest/gtest.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <string>
#include <vector>

#include <unistd.h>

namespace {

using nexusops::agent::SQLiteSpool;
using nexusops::agent::SpoolStatus;
using nexusops::agent::TelemetryPayloadFormat;
using nexusops::agent::TelemetryRecord;
using nexusops::agent::encodeNetworkSnapshot;
using nexusops::agent::encodeProcessSnapshot;
using nexusops::agent::encodeSystemMetrics;

template <
    std::size_t Capacity
>
void copyText(
    char (&destination)[Capacity],
    const std::string &value
)
{
    std::fill(
        std::begin(destination),
        std::end(destination),
        '\0'
    );

    const std::size_t length =
        std::min(
            value.size(),
            Capacity - 1
        );

    std::memcpy(
        destination,
        value.data(),
        length
    );
}

class TemporaryCodecSpool {
public:
    TemporaryCodecSpool()
    {
        path_ =
            (
                std::filesystem::
                    temp_directory_path() /
                (
                    "nexusops-codec-test-" +
                    std::to_string(
                        getpid()
                    ) +
                    ".db"
                )
            ).string();

        removeFiles();
    }

    ~TemporaryCodecSpool()
    {
        removeFiles();
    }

    [[nodiscard]]
    const std::string &path() const
    {
        return path_;
    }

private:
    void removeFiles()
    {
        std::error_code error;

        std::filesystem::remove(
            path_,
            error
        );

        error.clear();

        std::filesystem::remove(
            path_ + "-wal",
            error
        );

        error.clear();

        std::filesystem::remove(
            path_ + "-shm",
            error
        );
    }

    std::string path_;
};

TEST(
    TelemetryCodecTests,
    encodesSystemMetrics
)
{
    sysprobe_memory_info memory{};

    memory.total_bytes = 16000;
    memory.available_bytes = 6000;
    memory.used_bytes = 10000;

    sysprobe_filesystem_info
        filesystem{};

    filesystem.total_bytes = 50000;
    filesystem.free_bytes = 20000;
    filesystem.available_bytes = 18000;
    filesystem.used_bytes = 30000;

    sysprobe_uptime_info uptime{};

    uptime.uptime_seconds =
        12345.5;

    std::string payload;

    ASSERT_TRUE(
        encodeSystemMetrics(
            42.25,
            memory,
            filesystem,
            uptime,
            payload
        )
    );

    nexusops::telemetry::v1::
        SystemMetrics message;

    ASSERT_TRUE(
        message.ParseFromString(
            payload
        )
    );

    EXPECT_DOUBLE_EQ(
        message.cpu_usage_percent(),
        42.25
    );

    EXPECT_EQ(
        message.memory_used_bytes(),
        10000U
    );

    EXPECT_EQ(
        message.filesystem_used_bytes(),
        30000U
    );

    EXPECT_DOUBLE_EQ(
        message.uptime_seconds(),
        12345.5
    );
}

TEST(
    TelemetryCodecTests,
    encodesAllNetworkInterfaces
)
{
    sysprobe_network_interface
        first{};

    copyText(
        first.name,
        "eth0"
    );

    copyText(
        first.ipv4_address,
        "192.168.1.10"
    );

    copyText(
        first.ipv6_address,
        "fe80::1"
    );

    first.rx_bytes = 1000;
    first.rx_packets = 100;
    first.rx_errors = 2;
    first.rx_dropped = 3;
    first.tx_bytes = 2000;
    first.tx_packets = 200;
    first.tx_errors = 4;
    first.tx_dropped = 5;

    sysprobe_network_interface
        second{};

    copyText(
        second.name,
        "lo"
    );

    copyText(
        second.ipv4_address,
        "127.0.0.1"
    );

    copyText(
        second.ipv6_address,
        "::1"
    );

    second.rx_bytes = 3000;
    second.rx_packets = 300;
    second.tx_bytes = 4000;
    second.tx_packets = 400;

    const std::vector<
        sysprobe_network_interface
    > interfaces{
        first,
        second
    };

    std::string payload;

    ASSERT_TRUE(
        encodeNetworkSnapshot(
            interfaces,
            payload
        )
    );

    nexusops::telemetry::v1::
        NetworkSnapshot message;

    ASSERT_TRUE(
        message.ParseFromString(
            payload
        )
    );

    ASSERT_EQ(
        message.interfaces_size(),
        2
    );

    const auto &firstMessage =
        message.interfaces(0);

    EXPECT_EQ(
        firstMessage.name(),
        "eth0"
    );

    EXPECT_EQ(
        firstMessage.ipv4_address(),
        "192.168.1.10"
    );

    EXPECT_EQ(
        firstMessage.ipv6_address(),
        "fe80::1"
    );

    EXPECT_EQ(
        firstMessage.rx_bytes(),
        1000U
    );

    EXPECT_EQ(
        firstMessage.rx_packets(),
        100U
    );

    EXPECT_EQ(
        firstMessage.rx_errors(),
        2U
    );

    EXPECT_EQ(
        firstMessage.rx_dropped(),
        3U
    );

    EXPECT_EQ(
        firstMessage.tx_bytes(),
        2000U
    );

    EXPECT_EQ(
        firstMessage.tx_packets(),
        200U
    );

    EXPECT_EQ(
        firstMessage.tx_errors(),
        4U
    );

    EXPECT_EQ(
        firstMessage.tx_dropped(),
        5U
    );

    const auto &secondMessage =
        message.interfaces(1);

    EXPECT_EQ(
        secondMessage.name(),
        "lo"
    );

    EXPECT_EQ(
        secondMessage.ipv4_address(),
        "127.0.0.1"
    );

    EXPECT_EQ(
        secondMessage.ipv6_address(),
        "::1"
    );

    EXPECT_EQ(
        secondMessage.rx_bytes(),
        3000U
    );

    EXPECT_EQ(
        secondMessage.tx_bytes(),
        4000U
    );
}

TEST(
    TelemetryCodecTests,
    encodesAllProcesses
)
{
    sysprobe_process_info
        first{};

    first.pid = 100;
    first.parent_pid = 1;
    first.state = 'S';
    first.resident_memory_bytes =
        4096;

    copyText(
        first.name,
        "worker"
    );

    sysprobe_process_info
        second{};

    second.pid = 200;
    second.parent_pid = 100;
    second.state = 'R';
    second.resident_memory_bytes =
        8192;

    copyText(
        second.name,
        "collector"
    );

    const std::vector<
        sysprobe_process_info
    > processes{
        first,
        second
    };

    std::string payload;

    ASSERT_TRUE(
        encodeProcessSnapshot(
            processes,
            payload
        )
    );

    nexusops::telemetry::v1::
        ProcessSnapshot message;

    ASSERT_TRUE(
        message.ParseFromString(
            payload
        )
    );

    ASSERT_EQ(
        message.processes_size(),
        2
    );

    const auto &firstMessage =
        message.processes(0);

    EXPECT_EQ(
        firstMessage.pid(),
        100U
    );

    EXPECT_EQ(
        firstMessage.parent_pid(),
        1U
    );

    EXPECT_EQ(
        firstMessage.state(),
        "S"
    );

    EXPECT_EQ(
        firstMessage.name(),
        "worker"
    );

    EXPECT_EQ(
        firstMessage.
            resident_memory_bytes(),
        4096U
    );

    const auto &secondMessage =
        message.processes(1);

    EXPECT_EQ(
        secondMessage.pid(),
        200U
    );

    EXPECT_EQ(
        secondMessage.parent_pid(),
        100U
    );

    EXPECT_EQ(
        secondMessage.state(),
        "R"
    );

    EXPECT_EQ(
        secondMessage.name(),
        "collector"
    );

    EXPECT_EQ(
        secondMessage.
            resident_memory_bytes(),
        8192U
    );
}

TEST(
    TelemetryCodecTests,
    protobufPayloadSurvivesSpoolRoundTrip
)
{
    sysprobe_memory_info memory{};

    memory.used_bytes = 5000;

    sysprobe_filesystem_info
        filesystem{};

    filesystem.used_bytes = 9000;

    sysprobe_uptime_info uptime{};

    uptime.uptime_seconds = 120.5;

    std::string encodedPayload;

    ASSERT_TRUE(
        encodeSystemMetrics(
            75.5,
            memory,
            filesystem,
            uptime,
            encodedPayload
        )
    );

    TemporaryCodecSpool file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    std::int64_t sequence = 0;

    ASSERT_EQ(
        spool.enqueueProtobuf(
            "2026-08-25T16:00:00Z",
            "system",
            encodedPayload,
            &sequence
        ),
        SpoolStatus::ok
    );

    std::vector<
        TelemetryRecord
    > records;

    ASSERT_EQ(
        spool.peekOldest(
            10,
            records
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        records.size(),
        1U
    );

    EXPECT_EQ(
        records[0].sequence,
        sequence
    );

    EXPECT_EQ(
        records[0].payloadFormat,
        TelemetryPayloadFormat::
            protobuf
    );

    nexusops::telemetry::v1::
        SystemMetrics decoded;

    ASSERT_TRUE(
        decoded.ParseFromString(
            records[0].payload
        )
    );

    EXPECT_DOUBLE_EQ(
        decoded.cpu_usage_percent(),
        75.5
    );

    EXPECT_EQ(
        decoded.memory_used_bytes(),
        5000U
    );

    EXPECT_EQ(
        decoded.filesystem_used_bytes(),
        9000U
    );

    EXPECT_DOUBLE_EQ(
        decoded.uptime_seconds(),
        120.5
    );
}

}