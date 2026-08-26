#include "nexusops/telemetry/v1/telemetry.grpc.pb.h"
#include "nexusops/telemetry/v1/telemetry.pb.h"

#include <grpcpp/grpcpp.h>

#include <memory>

int main()
{
    nexusops::telemetry::v1::TelemetryEnvelope
        envelope;

    envelope.set_agent_id(
        "contract-test-agent"
    );

    envelope.set_batch_id(
        "contract-test-batch"
    );

    envelope.set_correlation_id(
        "contract-test-correlation"
    );

    auto *batch =
        envelope.mutable_metrics();

    auto *record =
        batch->add_records();

    record->set_sequence(
        1
    );

    auto *system =
        record->mutable_system();

    system->set_cpu_usage_percent(
        25.0
    );

    const auto channel =
        grpc::CreateChannel(
            "127.0.0.1:1",
            grpc::InsecureChannelCredentials()
        );

    auto stub =
        nexusops::telemetry::v1::
            TelemetryService::NewStub(
                channel
            );

    if (
        envelope.agent_id() !=
        "contract-test-agent"
    ) {
        return 1;
    }

    if (
        envelope.correlation_id() !=
        "contract-test-correlation"
    ) {
        return 1;
    }

    if (
        envelope.body_case() !=
        nexusops::telemetry::v1::
            TelemetryEnvelope::
                kMetrics
    ) {
        return 1;
    }

    if (
        batch->records_size() != 1
    ) {
        return 1;
    }

    if (!stub) {
        return 1;
    }

    return 0;
}
