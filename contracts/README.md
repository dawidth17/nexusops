# Contracts

Shared contracts between NexusOps components are versioned and stored in this directory.

The contract definitions are the source of truth. Generated code is treated as a build artifact and is not committed or edited manually.

## REST

REST APIs use versioned paths:

```text
/api/v1/...
```

Breaking API changes require a new version or an explicit migration.

OpenAPI definitions are stored in `openapi/`.

## Protobuf

Protobuf definitions are stored in:

```text
protobuf/
```

Packages include their contract version.

Example:

```text
nexusops.telemetry.v1
```

The first shared Protobuf contract is:

```text
protobuf/nexusops/telemetry/v1/telemetry.proto
```

It defines the communication boundary between SentinelAgent and OpsSight.

### Telemetry service

The telemetry service uses bidirectional gRPC streaming:

```text
SentinelAgent                         OpsSight
     │                                  │
     │──── TelemetryEnvelope ──────────>│
     │                                  │
     │<────── AgentControl ─────────────│
     │                                  │
```

The agent can send:

- heartbeats
- telemetry batches

OpsSight can send:

- acknowledgements
- backoff hints
- configuration version information

The control channel does not provide remote shell or arbitrary command execution.

### Telemetry batches

A telemetry envelope contains:

- `agent_id`
- `batch_id`
- `sent_at`
- an envelope payload

A metric batch contains one or more telemetry records.

Each telemetry record contains:

- a durable sequence number
- the capture timestamp
- one typed telemetry payload

Current payload types are:

- system metrics
- network snapshot
- process snapshot

The transport contract is intentionally different from the OpsSight database schema.

SentinelAgent sends typed snapshots. OpsSight is responsible for transforming accepted telemetry into its internal persistence model.

### Batch identity and acknowledgements

`batch_id` identifies one transmitted batch.

It can be used by the receiver to detect retransmission of a batch after a connection failure.

Each telemetry record also has a durable `sequence` value originating from the SentinelAgent spool.

OpsSight can acknowledge records cumulatively using:

```text
acknowledged_through_sequence
```

For example:

```text
spool sequences:
40 41 42 43 44

server acknowledgement:
acknowledged_through_sequence = 43

agent may remove:
40 41 42 43

agent keeps:
44
```

Batch identity and sequence numbers have different responsibilities:

```text
batch_id  -> transmission identity and deduplication
sequence  -> durable ordering and spool acknowledgement
```

### Compatibility rules

Changes inside a Protobuf major version should remain backward compatible.

Rules:

- existing field numbers must never be reused
- existing fields should not be renumbered
- incompatible field type changes are not allowed
- new optional fields and new message variants should be preferred over changing existing semantics
- removed field numbers and names should be reserved
- breaking changes require a new package version, such as `nexusops.telemetry.v2`
- timestamps use UTC
- standard Protobuf timestamp types are preferred where appropriate
- secrets must never be included in contract definitions or examples

### Code generation

Generated Protobuf and gRPC code is not committed.

The same `.proto` source is used to generate:

```text
C++
├── telemetry.pb.cc
├── telemetry.pb.h
├── telemetry.grpc.pb.cc
└── telemetry.grpc.pb.h

Python
├── telemetry_pb2.py
└── telemetry_pb2_grpc.py
```

C++ generated code is validated through CMake.

Python generated code is created in a temporary directory and imported by a smoke test.

This prevents generated code from becoming out of sync with the contract source.

## Events

Events use a common envelope containing:

- `eventId`
- `eventType`
- `schemaVersion`
- `occurredAt`
- `source`
- `correlationId`
- `payload`

Event schemas are stored in `events/`.

## General rules

- timestamps use UTC
- `correlationId` is propagated across related operations
- breaking contract changes must be versioned
- secrets are never included in contracts or examples