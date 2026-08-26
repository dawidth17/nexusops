#include "nexusops/agent/grpc_telemetry_client.h"
#include "nexusops/agent/sqlite_spool.h"
#include "nexusops/telemetry/v1/telemetry.grpc.pb.h"
#include "nexusops/telemetry/v1/telemetry.pb.h"

#include <grpcpp/grpcpp.h>
#include <gtest/gtest.h>

#include <atomic>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include <unistd.h>

namespace {

namespace telemetry_v1 =
    ::nexusops::telemetry::v1;

using nexusops::agent::
    GrpcTelemetryClient;

using nexusops::agent::
    GrpcTelemetryClientOptions;

using nexusops::agent::
    SQLiteSpool;

using nexusops::agent::
    SpoolStatus;

using nexusops::agent::
    TelemetryFlushStatus;

std::atomic<std::uint64_t>
    nextTestId{0};

class TemporarySpoolFile {
public:
    TemporarySpoolFile()
    {
        const auto id =
            nextTestId.fetch_add(
                1
            );

        path_ =
            (
                std::filesystem::
                    temp_directory_path() /
                (
                    "nexusops-grpc-test-" +
                    std::to_string(
                        getpid()
                    ) +
                    "-" +
                    std::to_string(
                        id
                    ) +
                    ".db"
                )
            ).string();
    }

    ~TemporarySpoolFile()
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

class FakeTelemetryService final
    : public telemetry_v1::
          TelemetryService::
          Service {
public:
    explicit FakeTelemetryService(
        bool dropFirstMetricAck = false
    )
        : dropFirstMetricAck_(
              dropFirstMetricAck
          )
    {
    }

    grpc::Status StreamTelemetry(
        grpc::ServerContext *,
        grpc::ServerReaderWriter<
            telemetry_v1::AgentControl,
            telemetry_v1::TelemetryEnvelope
        > *stream
    ) override
    {
        telemetry_v1::TelemetryEnvelope
            envelope;

        while (
            stream->Read(
                &envelope
            )
        ) {
            if (
                envelope.has_heartbeat()
            ) {
                telemetry_v1::AgentControl
                    control;

                auto *ack =
                    control.mutable_ack();

                ack->set_batch_id(
                    envelope.batch_id()
                );

                ack->
                    set_acknowledged_through_sequence(
                        0
                    );

                if (
                    !stream->Write(
                        control
                    )
                ) {
                    return grpc::Status(
                        grpc::StatusCode::
                            UNAVAILABLE,
                        "failed to write heartbeat acknowledgement"
                    );
                }

                continue;
            }

            if (
                !envelope.has_metrics()
            ) {
                return grpc::Status(
                    grpc::StatusCode::
                        INVALID_ARGUMENT,
                    "unexpected envelope body"
                );
            }

            std::vector<
                std::int64_t
            > sequences;

            for (
                const auto &record :
                envelope.
                    metrics().
                    records()
            ) {
                sequences.push_back(
                    record.sequence()
                );
            }

            bool dropAck = false;

            {
                std::lock_guard lock(
                    mutex_
                );

                metricBatchIds_.
                    push_back(
                        envelope.batch_id()
                    );

                metricCorrelationIds_.
                    push_back(
                        envelope.correlation_id()
                    );

                metricSequences_.
                    push_back(
                        sequences
                    );

                if (
                    dropFirstMetricAck_ &&
                    !firstMetricAckDropped_
                ) {
                    firstMetricAckDropped_ =
                        true;

                    dropAck = true;
                }
            }

            if (dropAck) {
                return grpc::Status(
                    grpc::StatusCode::
                        UNAVAILABLE,
                    "simulated transport failure"
                );
            }

            telemetry_v1::AgentControl
                control;

            auto *ack =
                control.mutable_ack();

            ack->set_batch_id(
                envelope.batch_id()
            );

            if (!sequences.empty()) {
                ack->
                    set_acknowledged_through_sequence(
                        sequences.back()
                    );
            }

            if (
                !stream->Write(
                    control
                )
            ) {
                return grpc::Status(
                    grpc::StatusCode::
                        UNAVAILABLE,
                    "failed to write telemetry acknowledgement"
                );
            }
        }

        return grpc::Status::OK;
    }

    [[nodiscard]]
    std::vector<std::string>
    metricBatchIds() const
    {
        std::lock_guard lock(
            mutex_
        );

        return metricBatchIds_;
    }

    [[nodiscard]]
    std::vector<std::string>
    metricCorrelationIds() const
    {
        std::lock_guard lock(
            mutex_
        );

        return metricCorrelationIds_;
    }

    [[nodiscard]]
    std::vector<
        std::vector<std::int64_t>
    >
    metricSequences() const
    {
        std::lock_guard lock(
            mutex_
        );

        return metricSequences_;
    }

private:
    bool dropFirstMetricAck_{
        false
    };

    bool firstMetricAckDropped_{
        false
    };

    mutable std::mutex mutex_;

    std::vector<std::string>
        metricBatchIds_;

    std::vector<std::string>
        metricCorrelationIds_;

    std::vector<
        std::vector<std::int64_t>
    > metricSequences_;
};

class TestGrpcServer {
public:
    explicit TestGrpcServer(
        grpc::Service *service
    )
    {
        grpc::ServerBuilder builder;

        int selectedPort = 0;

        builder.AddListeningPort(
            "127.0.0.1:0",
            grpc::
                InsecureServerCredentials(),
            &selectedPort
        );

        builder.RegisterService(
            service
        );

        server_ =
            builder.BuildAndStart();

        if (
            server_ == nullptr ||
            selectedPort <= 0
        ) {
            throw std::runtime_error(
                "failed to start test gRPC server"
            );
        }

        endpoint_ =
            "127.0.0.1:" +
            std::to_string(
                selectedPort
            );
    }

    ~TestGrpcServer()
    {
        if (server_ != nullptr) {
            server_->Shutdown();

            server_->Wait();
        }
    }

    [[nodiscard]]
    const std::string &
    endpoint() const
    {
        return endpoint_;
    }

private:
    std::string endpoint_;

    std::unique_ptr<
        grpc::Server
    > server_;
};

std::string makeSystemPayload(
    double cpuUsage
)
{
    telemetry_v1::SystemMetrics
        metrics;

    metrics.set_cpu_usage_percent(
        cpuUsage
    );

    metrics.set_memory_used_bytes(
        4096
    );

    metrics.set_filesystem_used_bytes(
        8192
    );

    metrics.set_uptime_seconds(
        120.0
    );

    std::string payload;

    if (
        !metrics.SerializeToString(
            &payload
        )
    ) {
        throw std::runtime_error(
            "failed to encode test telemetry"
        );
    }

    return payload;
}

GrpcTelemetryClientOptions
makeOptions(
    const std::string &endpoint
)
{
    GrpcTelemetryClientOptions
        options;

    options.endpoint =
        endpoint;

    options.agentId =
        "grpc-test-agent";

    options.hostname =
        "grpc-test-host";

    options.agentVersion =
        "0.9.0";

    options.maxBatchRecords = 16;

    options.rpcDeadline =
        std::chrono::
            milliseconds(
                1000
            );

    return options;
}

bool isLowercaseHex(
    const std::string &value
)
{
    for (
        const char character :
        value
    ) {
        const bool digit =
            character >= '0' &&
            character <= '9';

        const bool hexLetter =
            character >= 'a' &&
            character <= 'f';

        if (
            !digit &&
            !hexLetter
        ) {
            return false;
        }
    }

    return true;
}

TEST(
    GrpcTelemetryClientTests,
    acknowledgesSentRecords
)
{
    FakeTelemetryService service;

    TestGrpcServer server(
        &service
    );

    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    std::int64_t sequence = 0;

    ASSERT_EQ(
        spool.enqueueProtobuf(
            "2026-08-25T12:00:00Z",
            "system",
            makeSystemPayload(
                25.0
            ),
            &sequence
        ),
        SpoolStatus::ok
    );

    GrpcTelemetryClient client(
        makeOptions(
            server.endpoint()
        )
    );

    const auto result =
        client.flush(
            spool
        );

    EXPECT_EQ(
        result.status,
        TelemetryFlushStatus::ok
    );

    EXPECT_EQ(
        result.sentRecords,
        1U
    );

    EXPECT_EQ(
        result.acknowledgedRecords,
        1U
    );

    std::size_t count = 99;

    ASSERT_EQ(
        spool.count(
            count
        ),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        count,
        0U
    );

    const auto sequences =
        service.metricSequences();

    ASSERT_EQ(
        sequences.size(),
        1U
    );

    ASSERT_EQ(
        sequences[0].size(),
        1U
    );

    EXPECT_EQ(
        sequences[0][0],
        sequence
    );

    const auto correlationIds =
        service.metricCorrelationIds();

    ASSERT_EQ(
        correlationIds.size(),
        1U
    );

    EXPECT_EQ(
        correlationIds[0].size(),
        64U
    );

    EXPECT_TRUE(
        isLowercaseHex(
            correlationIds[0]
        )
    );
}

TEST(
    GrpcTelemetryClientTests,
    keepsSpoolWhenServerIsUnavailable
)
{
    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    std::int64_t sequence = 0;

    ASSERT_EQ(
        spool.enqueueProtobuf(
            "2026-08-25T12:00:00Z",
            "system",
            makeSystemPayload(
                30.0
            ),
            &sequence
        ),
        SpoolStatus::ok
    );

    auto options =
        makeOptions(
            "127.0.0.1:1"
        );

    options.rpcDeadline =
        std::chrono::
            milliseconds(
                200
            );

    GrpcTelemetryClient client(
        std::move(
            options
        )
    );

    const auto result =
        client.flush(
            spool
        );

    EXPECT_EQ(
        result.status,
        TelemetryFlushStatus::
            transport_error
    );

    std::size_t count = 0;

    ASSERT_EQ(
        spool.count(
            count
        ),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        count,
        1U
    );
}

TEST(
    GrpcTelemetryClientTests,
    reconnectsAndResendsSameBatchInOrder
)
{
    FakeTelemetryService service(
        true
    );

    TestGrpcServer server(
        &service
    );

    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    std::int64_t firstSequence = 0;
    std::int64_t secondSequence = 0;

    ASSERT_EQ(
        spool.enqueueProtobuf(
            "2026-08-25T12:00:00Z",
            "system",
            makeSystemPayload(
                10.0
            ),
            &firstSequence
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        spool.enqueueProtobuf(
            "2026-08-25T12:00:01Z",
            "system",
            makeSystemPayload(
                20.0
            ),
            &secondSequence
        ),
        SpoolStatus::ok
    );

    GrpcTelemetryClient client(
        makeOptions(
            server.endpoint()
        )
    );

    const auto firstResult =
        client.flush(
            spool
        );

    EXPECT_EQ(
        firstResult.status,
        TelemetryFlushStatus::
            transport_error
    );

    std::size_t count = 0;

    ASSERT_EQ(
        spool.count(
            count
        ),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        count,
        2U
    );

    const auto secondResult =
        client.flush(
            spool
        );

    EXPECT_EQ(
        secondResult.status,
        TelemetryFlushStatus::ok
    );

    EXPECT_EQ(
        secondResult.
            acknowledgedRecords,
        2U
    );

    ASSERT_EQ(
        spool.count(
            count
        ),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        count,
        0U
    );

    const auto batchIds =
        service.metricBatchIds();

    ASSERT_EQ(
        batchIds.size(),
        2U
    );

    EXPECT_EQ(
        batchIds[0],
        batchIds[1]
    );

    const auto correlationIds =
        service.metricCorrelationIds();

    ASSERT_EQ(
        correlationIds.size(),
        2U
    );

    EXPECT_FALSE(
        correlationIds[0].empty()
    );

    EXPECT_EQ(
        correlationIds[0].size(),
        64U
    );

    EXPECT_TRUE(
        isLowercaseHex(
            correlationIds[0]
        )
    );

    EXPECT_EQ(
        correlationIds[0],
        correlationIds[1]
    );

    const auto sequences =
        service.metricSequences();

    ASSERT_EQ(
        sequences.size(),
        2U
    );

    for (
        const auto &attempt :
        sequences
    ) {
        ASSERT_EQ(
            attempt.size(),
            2U
        );

        EXPECT_EQ(
            attempt[0],
            firstSequence
        );

        EXPECT_EQ(
            attempt[1],
            secondSequence
        );
    }
}

}
