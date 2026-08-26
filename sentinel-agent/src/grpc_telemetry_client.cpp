#include "nexusops/agent/grpc_telemetry_client.h"

#include "nexusops/agent/sqlite_spool.h"
#include "nexusops/telemetry/v1/telemetry.grpc.pb.h"
#include "nexusops/telemetry/v1/telemetry.pb.h"

#include <google/protobuf/util/time_util.h>
#include <grpcpp/grpcpp.h>

#include <chrono>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace {

namespace telemetry_v1 =
    ::nexusops::telemetry::v1;

using nexusops::agent::
    TelemetryPayloadFormat;

using nexusops::agent::
    TelemetryRecord;

std::string makeHeartbeatBatchId()
{
    const auto now =
        std::chrono::system_clock::now();

    const auto microseconds =
        std::chrono::duration_cast<
            std::chrono::microseconds
        >(
            now.time_since_epoch()
        ).count();

    return
        "heartbeat-" +
        std::to_string(
            microseconds
        );
}

std::string makeTelemetryBatchId(
    const std::vector<
        TelemetryRecord
    > &records
)
{
    const auto &first =
        records.front();

    const auto &last =
        records.back();

    return
        "spool-" +
        std::to_string(
            first.sequence
        ) +
        "-" +
        std::to_string(
            last.sequence
        ) +
        "-" +
        first.capturedAtUtc +
        "-" +
        last.capturedAtUtc;
}

std::string grpcStatusMessage(
    const grpc::Status &status,
    const std::string &fallback
)
{
    if (
        !status.error_message().
            empty()
    ) {
        return status.error_message();
    }

    return
        fallback +
        " code=" +
        std::to_string(
            static_cast<int>(
                status.error_code()
            )
        );
}

std::string readRequiredFile(
    const std::string &path,
    const std::string &description
)
{
    if (path.empty()) {
        throw std::invalid_argument(
            description +
            " path must not be empty"
        );
    }

    std::ifstream input(
        path,
        std::ios::binary
    );

    if (!input) {
        throw std::runtime_error(
            "unable to read " +
            description +
            ": " +
            path
        );
    }

    const std::string content{
        std::istreambuf_iterator<char>{
            input
        },
        std::istreambuf_iterator<char>{}
    };

    if (content.empty()) {
        throw std::runtime_error(
            description +
            " is empty: " +
            path
        );
    }

    return content;
}

std::shared_ptr<
    grpc::ChannelCredentials
>
buildChannelCredentials(
    const nexusops::agent::
        GrpcTelemetryClientOptions
        &options
)
{
    if (!options.mtls.enabled) {
        return grpc::
            InsecureChannelCredentials();
    }

    grpc::SslCredentialsOptions
        sslOptions;

    sslOptions.pem_root_certs =
        readRequiredFile(
            options.mtls.
                caCertificatePath,
            "OpsSight mTLS CA certificate"
        );

    sslOptions.pem_cert_chain =
        readRequiredFile(
            options.mtls.
                clientCertificatePath,
            "SentinelAgent mTLS client certificate"
        );

    sslOptions.pem_private_key =
        readRequiredFile(
            options.mtls.
                clientPrivateKeyPath,
            "SentinelAgent mTLS client private key"
        );

    return grpc::SslCredentials(
        sslOptions
    );
}

bool decodeTelemetryPayload(
    const TelemetryRecord &record,
    telemetry_v1::TelemetryRecord
        &message,
    std::string &error
)
{
    if (
        record.payloadFormat !=
        TelemetryPayloadFormat::
            protobuf
    ) {
        error =
            "spool record uses legacy payload format";

        return false;
    }

    message.set_sequence(
        record.sequence
    );

    if (
        !google::protobuf::util::
            TimeUtil::FromString(
                record.capturedAtUtc,
                message.mutable_captured_at()
            )
    ) {
        error =
            "invalid captured_at timestamp";

        return false;
    }

    if (record.kind == "system") {
        telemetry_v1::SystemMetrics
            payload;

        if (
            !payload.ParseFromString(
                record.payload
            )
        ) {
            error =
                "failed to decode system protobuf payload";

            return false;
        }

        message.mutable_system()->
            CopyFrom(
                payload
            );

        return true;
    }

    if (record.kind == "network") {
        telemetry_v1::NetworkSnapshot
            payload;

        if (
            !payload.ParseFromString(
                record.payload
            )
        ) {
            error =
                "failed to decode network protobuf payload";

            return false;
        }

        message.mutable_network()->
            CopyFrom(
                payload
            );

        return true;
    }

    if (record.kind == "processes") {
        telemetry_v1::ProcessSnapshot
            payload;

        if (
            !payload.ParseFromString(
                record.payload
            )
        ) {
            error =
                "failed to decode process protobuf payload";

            return false;
        }

        message.mutable_processes()->
            CopyFrom(
                payload
            );

        return true;
    }

    error =
        "unsupported telemetry kind: " +
        record.kind;

    return false;
}

}

namespace nexusops::agent {

struct GrpcTelemetryClient::Impl {
    explicit Impl(
        GrpcTelemetryClientOptions
            clientOptions
    )
        : options(
              std::move(
                  clientOptions
              )
          ),
          heartbeatBatchId(
              makeHeartbeatBatchId()
          )
    {
        if (options.endpoint.empty()) {
            throw std::invalid_argument(
                "gRPC endpoint must not be empty"
            );
        }

        if (
            options.agentId.empty() ||
            options.agentId.size() > 200
        ) {
            throw std::invalid_argument(
                "agent id must contain between 1 and 200 characters"
            );
        }

        if (
            options.hostname.empty() ||
            options.hostname.size() > 200
        ) {
            throw std::invalid_argument(
                "hostname must contain between 1 and 200 characters"
            );
        }

        if (
            options.agentVersion.size() >
            64
        ) {
            throw std::invalid_argument(
                "agent version exceeds 64 characters"
            );
        }

        if (
            options.maxBatchRecords == 0
        ) {
            throw std::invalid_argument(
                "max batch records must be greater than zero"
            );
        }

        if (
            options.rpcDeadline <=
            std::chrono::milliseconds::zero()
        ) {
            throw std::invalid_argument(
                "RPC deadline must be greater than zero"
            );
        }

        channel =
            grpc::CreateChannel(
                options.endpoint,
                buildChannelCredentials(
                    options
                )
            );

        stub =
            telemetry_v1::
                TelemetryService::
                NewStub(
                    channel
                );
    }

    telemetry_v1::TelemetryEnvelope
    buildHeartbeat() const
    {
        telemetry_v1::TelemetryEnvelope
            envelope;

        envelope.set_agent_id(
            options.agentId
        );

        envelope.set_batch_id(
            heartbeatBatchId
        );

        envelope.mutable_sent_at()->
            CopyFrom(
                google::protobuf::util::
                    TimeUtil::
                    GetCurrentTime()
            );

        auto *heartbeat =
            envelope.mutable_heartbeat();

        heartbeat->set_hostname(
            options.hostname
        );

        heartbeat->
            set_agent_version(
                options.agentVersion
            );

        return envelope;
    }

    bool buildMetricBatch(
        const std::vector<
            TelemetryRecord
        > &records,
        telemetry_v1::TelemetryEnvelope
            &envelope,
        std::string &error
    ) const
    {
        if (records.empty()) {
            error =
                "cannot build an empty telemetry batch";

            return false;
        }

        envelope.set_agent_id(
            options.agentId
        );

        envelope.set_batch_id(
            makeTelemetryBatchId(
                records
            )
        );

        envelope.mutable_sent_at()->
            CopyFrom(
                google::protobuf::util::
                    TimeUtil::
                    GetCurrentTime()
            );

        auto *metrics =
            envelope.mutable_metrics();

        for (
            const auto &record :
            records
        ) {
            auto *message =
                metrics->add_records();

            if (
                !decodeTelemetryPayload(
                    record,
                    *message,
                    error
                )
            ) {
                return false;
            }
        }

        return true;
    }

    static bool validateAck(
        const telemetry_v1::AgentControl
            &control,
        const std::string
            &expectedBatchId,
        std::int64_t
            expectedSequence,
        std::string &error
    )
    {
        if (!control.has_ack()) {
            if (
                control.has_backoff_hint()
            ) {
                error =
                    "server requested backoff: " +
                    control.
                        backoff_hint().
                        reason();

                return false;
            }

            error =
                "server response did not contain an acknowledgement";

            return false;
        }

        const auto &ack =
            control.ack();

        if (
            ack.batch_id() !=
            expectedBatchId
        ) {
            error =
                "server acknowledgement batch id does not match request";

            return false;
        }

        if (
            ack.
                acknowledged_through_sequence()
            != expectedSequence
        ) {
            error =
                "server acknowledgement sequence does not match request";

            return false;
        }

        return true;
    }

    TelemetryFlushResult flush(
        SQLiteSpool &spool
    )
    {
        std::vector<
            TelemetryRecord
        > records;

        const auto spoolStatus =
            spool.peekOldest(
                options.maxBatchRecords,
                records
            );

        if (
            spoolStatus !=
            SpoolStatus::ok
        ) {
            return {
                TelemetryFlushStatus::
                    spool_error,
                0,
                0,
                spool.lastError()
            };
        }

        grpc::ClientContext context;

        context.set_deadline(
            std::chrono::
                system_clock::now() +
            options.rpcDeadline
        );

        auto stream =
            stub->StreamTelemetry(
                &context
            );

        if (!stream) {
            return {
                TelemetryFlushStatus::
                    transport_error,
                records.size(),
                0,
                "failed to create gRPC telemetry stream"
            };
        }

        const auto heartbeat =
            buildHeartbeat();

        if (
            !stream->Write(
                heartbeat
            )
        ) {
            stream->WritesDone();

            const auto status =
                stream->Finish();

            return {
                TelemetryFlushStatus::
                    transport_error,
                records.size(),
                0,
                grpcStatusMessage(
                    status,
                    "failed to send heartbeat"
                )
            };
        }

        telemetry_v1::AgentControl
            heartbeatControl;

        if (
            !stream->Read(
                &heartbeatControl
            )
        ) {
            stream->WritesDone();

            const auto status =
                stream->Finish();

            return {
                TelemetryFlushStatus::
                    transport_error,
                records.size(),
                0,
                grpcStatusMessage(
                    status,
                    "heartbeat acknowledgement was not received"
                )
            };
        }

        std::string
            acknowledgementError;

        if (
            !validateAck(
                heartbeatControl,
                heartbeat.batch_id(),
                0,
                acknowledgementError
            )
        ) {
            context.TryCancel();

            stream->WritesDone();
            stream->Finish();

            const auto status =
                heartbeatControl.
                    has_backoff_hint()
                    ? TelemetryFlushStatus::
                        backoff
                    : TelemetryFlushStatus::
                        protocol_error;

            return {
                status,
                records.size(),
                0,
                acknowledgementError
            };
        }

        if (records.empty()) {
            stream->WritesDone();

            const auto status =
                stream->Finish();

            if (!status.ok()) {
                return {
                    TelemetryFlushStatus::
                        transport_error,
                    0,
                    0,
                    grpcStatusMessage(
                        status,
                        "gRPC heartbeat stream failed"
                    )
                };
            }

            return {
                TelemetryFlushStatus::
                    nothing_to_send,
                0,
                0,
                ""
            };
        }

        telemetry_v1::TelemetryEnvelope
            metricEnvelope;

        std::string payloadError;

        if (
            !buildMetricBatch(
                records,
                metricEnvelope,
                payloadError
            )
        ) {
            context.TryCancel();

            stream->WritesDone();
            stream->Finish();

            return {
                TelemetryFlushStatus::
                    invalid_payload,
                records.size(),
                0,
                payloadError
            };
        }

        if (
            !stream->Write(
                metricEnvelope
            )
        ) {
            stream->WritesDone();

            const auto status =
                stream->Finish();

            return {
                TelemetryFlushStatus::
                    transport_error,
                records.size(),
                0,
                grpcStatusMessage(
                    status,
                    "failed to send telemetry batch"
                )
            };
        }

        telemetry_v1::AgentControl
            metricControl;

        if (
            !stream->Read(
                &metricControl
            )
        ) {
            stream->WritesDone();

            const auto status =
                stream->Finish();

            return {
                TelemetryFlushStatus::
                    transport_error,
                records.size(),
                0,
                grpcStatusMessage(
                    status,
                    "telemetry acknowledgement was not received"
                )
            };
        }

        if (
            !validateAck(
                metricControl,
                metricEnvelope.batch_id(),
                records.back().sequence,
                acknowledgementError
            )
        ) {
            context.TryCancel();

            stream->WritesDone();
            stream->Finish();

            const auto status =
                metricControl.
                    has_backoff_hint()
                    ? TelemetryFlushStatus::
                        backoff
                    : TelemetryFlushStatus::
                        protocol_error;

            return {
                status,
                records.size(),
                0,
                acknowledgementError
            };
        }

        stream->WritesDone();

        const auto status =
            stream->Finish();

        if (!status.ok()) {
            return {
                TelemetryFlushStatus::
                    transport_error,
                records.size(),
                0,
                grpcStatusMessage(
                    status,
                    "gRPC telemetry stream failed"
                )
            };
        }

        std::size_t
            removedCount = 0;

        const auto acknowledgeStatus =
            spool.acknowledgeThrough(
                records.back().sequence,
                removedCount
            );

        if (
            acknowledgeStatus !=
            SpoolStatus::ok
        ) {
            return {
                TelemetryFlushStatus::
                    spool_error,
                records.size(),
                0,
                spool.lastError()
            };
        }

        if (
            removedCount !=
            records.size()
        ) {
            return {
                TelemetryFlushStatus::
                    spool_error,
                records.size(),
                removedCount,
                "spool acknowledgement removed an unexpected number of records"
            };
        }

        return {
            TelemetryFlushStatus::ok,
            records.size(),
            removedCount,
            ""
        };
    }

    GrpcTelemetryClientOptions
        options;

    std::string heartbeatBatchId;

    std::shared_ptr<
        grpc::Channel
    > channel;

    std::unique_ptr<
        telemetry_v1::
            TelemetryService::
            Stub
    > stub;
};

GrpcTelemetryClient::
GrpcTelemetryClient(
    GrpcTelemetryClientOptions options
)
    : impl_(
          std::make_unique<Impl>(
              std::move(options)
          )
      )
{
}

GrpcTelemetryClient::
~GrpcTelemetryClient() = default;

GrpcTelemetryClient::
GrpcTelemetryClient(
    GrpcTelemetryClient &&
) noexcept = default;

GrpcTelemetryClient &
GrpcTelemetryClient::operator=(
    GrpcTelemetryClient &&
) noexcept = default;

TelemetryFlushResult
GrpcTelemetryClient::flush(
    SQLiteSpool &spool
)
{
    return impl_->flush(
        spool
    );
}

}