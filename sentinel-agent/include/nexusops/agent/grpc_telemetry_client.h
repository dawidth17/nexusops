#ifndef NEXUSOPS_GRPC_TELEMETRY_CLIENT_H
#define NEXUSOPS_GRPC_TELEMETRY_CLIENT_H

#include <chrono>
#include <cstddef>
#include <memory>
#include <string>

namespace nexusops::agent {

class SQLiteSpool;

enum class TelemetryFlushStatus {
    ok,
    nothing_to_send,
    transport_error,
    protocol_error,
    spool_error,
    invalid_payload,
    backoff
};

struct TelemetryFlushResult {
    TelemetryFlushStatus status{
        TelemetryFlushStatus::ok
    };

    std::size_t sentRecords{0};

    std::size_t acknowledgedRecords{0};

    std::string message;
};

struct GrpcTelemetryClientMtlsOptions {
    bool enabled{false};

    std::string caCertificatePath;

    std::string clientCertificatePath;

    std::string clientPrivateKeyPath;
};

struct GrpcTelemetryClientOptions {
    std::string endpoint{
        "127.0.0.1:50051"
    };

    std::string agentId;

    std::string hostname;

    std::string agentVersion;

    std::size_t maxBatchRecords{16};

    std::chrono::milliseconds
        rpcDeadline{
            1500
        };

    GrpcTelemetryClientMtlsOptions
        mtls;
};

class GrpcTelemetryClient {
public:
    explicit GrpcTelemetryClient(
        GrpcTelemetryClientOptions options
    );

    ~GrpcTelemetryClient();

    GrpcTelemetryClient(
        const GrpcTelemetryClient &
    ) = delete;

    GrpcTelemetryClient &operator=(
        const GrpcTelemetryClient &
    ) = delete;

    GrpcTelemetryClient(
        GrpcTelemetryClient &&
    ) noexcept;

    GrpcTelemetryClient &operator=(
        GrpcTelemetryClient &&
    ) noexcept;

    TelemetryFlushResult flush(
        SQLiteSpool &spool
    );

private:
    struct Impl;

    std::unique_ptr<Impl> impl_;
};

}

#endif