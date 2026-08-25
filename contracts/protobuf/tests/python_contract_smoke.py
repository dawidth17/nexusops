from nexusops.telemetry.v1 import (
    telemetry_pb2,
    telemetry_pb2_grpc,
)


def main() -> None:
    envelope = telemetry_pb2.TelemetryEnvelope(
        agent_id="contract-test-agent",
        batch_id="contract-test-batch",
    )

    record = envelope.metrics.records.add()
    record.sequence = 1
    record.system.cpu_usage_percent = 25.0

    assert (
        envelope.DESCRIPTOR.full_name
        == "nexusops.telemetry.v1.TelemetryEnvelope"
    )

    assert envelope.WhichOneof("body") == "metrics"

    assert len(envelope.metrics.records) == 1

    assert hasattr(
        telemetry_pb2_grpc,
        "TelemetryServiceStub",
    )

    service = telemetry_pb2.DESCRIPTOR.services_by_name[
        "TelemetryService"
    ]

    assert (
        service.full_name
        == "nexusops.telemetry.v1.TelemetryService"
    )

    print(
        "python telemetry contract smoke test passed"
    )


if __name__ == "__main__":
    main()