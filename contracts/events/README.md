# NexusOps Event Contracts

NexusOps uses Kafka for durable domain events exchanged between applications.

RabbitMQ has a separate responsibility and remains the Celery task broker used internally by OpsSight.

## Topic naming

Production Kafka topics use:

```text
nexusops.<producer>.<stream>.v<major>
```

Segments are lowercase.

Hyphens may be used inside a segment.

Underscores are not used.

The current topics are:

```text
nexusops.opssight.alert-lifecycle.v1
nexusops.servicecore.incident-lifecycle.v1
```

The producer segment identifies the application that owns the event stream.

Examples:

```text
opssight
servicecore
```

The final `v<major>` segment is the major contract version.

## Topic versioning

Backward-compatible changes stay on the same major topic.

Examples of backward-compatible changes include:

- adding optional fields
- adding a new event type that existing consumers can safely ignore
- adding documentation or examples

Breaking changes require a new topic major version.

Example:

```text
nexusops.opssight.alert-lifecycle.v1
```

becomes:

```text
nexusops.opssight.alert-lifecycle.v2
```

A migration may temporarily run both versions while consumers move to the new contract.

Existing topic semantics must not be silently changed.

## Event envelope

All NexusOps domain events use the common event envelope defined in:

```text
v1/event-envelope.schema.json
```

The envelope contains:

```text
eventId
eventType
schemaVersion
occurredAt
source
correlationId
payload
```

### eventId

`eventId` uniquely identifies one domain event.

It is a UUID.

Kafka delivery is treated as at-least-once, so consumers must be able to handle the same `eventId` more than once.

Persistent consumers should use `eventId` for idempotency where appropriate.

### eventType

Event types use lowercase dotted names:

```text
nexusops.<aggregate>.<event>
```

Examples:

```text
nexusops.alert.opened
nexusops.alert.recovered
nexusops.incident.created
nexusops.incident.resolved
```

An event type describes something that already happened.

### schemaVersion

`schemaVersion` identifies the major JSON event contract version.

Version 1 events use:

```json
{
  "schemaVersion": 1
}
```

A breaking event contract requires a new major version.

### occurredAt

`occurredAt` records when the domain event occurred.

Timestamps use UTC and must use the `Z` suffix.

Example:

```text
2026-08-26T10:00:00Z
```

Local-time timestamps and timestamps with non-UTC offsets are not valid event-contract timestamps.

### source

`source` identifies the application that produced the event.

Current values include:

```text
opssight
servicecore
```

### correlationId

`correlationId` connects events and operations that belong to the same cross-service workflow.

It must be propagated rather than replaced when an existing correlation identity is available.

### payload

`payload` contains event-specific domain data.

Payload structure is defined by the corresponding versioned event schema.

## Alert lifecycle

OpsSight publishes alert lifecycle events to:

```text
nexusops.opssight.alert-lifecycle.v1
```

The schema is:

```text
v1/alert-lifecycle.schema.json
```

Current event types are:

```text
nexusops.alert.opened
nexusops.alert.recovered
```

The Kafka message key for alert events must be:

```text
alertId
```

Using the stable alert ID as the message key keeps events for the same alert on the same Kafka partition.

This provides ordering for one alert without requiring global ordering across all alerts.

## Incident lifecycle

ServiceCore owns the incident lifecycle stream:

```text
nexusops.servicecore.incident-lifecycle.v1
```

The schema is:

```text
v1/incident-lifecycle.schema.json
```

Current event types are:

```text
nexusops.incident.created
nexusops.incident.updated
nexusops.incident.resolved
nexusops.incident.closed
```

The Kafka message key for incident events must be:

```text
incidentId
```

This keeps events for one incident on the same Kafka partition.

## Delivery semantics

NexusOps treats Kafka event publication and consumption as at-least-once.

This means duplicates are possible.

Consumers must not assume that receiving a Kafka record means the event has never been processed before.

Where processing has side effects, idempotency should be based on persistent event or business identity.

Ordering is guaranteed only within a Kafka partition.

Applications must use the documented aggregate ID as the Kafka message key when aggregate ordering is required.

## Schema compatibility

Version 1 schemas are stored under:

```text
v1/
```

Within one major version:

- required fields must not be removed
- existing fields must not change meaning
- incompatible field type changes are not allowed
- existing event types must not silently change semantics
- new fields should normally be optional
- timestamps must remain UTC
- secrets must never appear in events

Breaking changes require a new major contract and topic version.

## Examples

Example events are stored in:

```text
v1/examples/
```

Examples are validated against their JSON schemas in CI.

## Local Kafka

Kafka is provided by the NexusOps Docker Compose development stack.

The host bootstrap server is:

```text
127.0.0.1:9092
```

Containers inside the NexusOps Docker network use:

```text
kafka:19092
```

Kafka runs in KRaft mode and does not require ZooKeeper.

Automatic topic creation is disabled.

Versioned NexusOps topics are explicitly provisioned by:

```text
infra/kafka/create-topics.sh
```

## Smoke test

The Kafka smoke test:

1. creates a temporary versioned topic
2. serializes a valid NexusOps event
3. publishes it through Kafka
4. consumes it back through Kafka
5. compares the consumed JSON with the produced JSON
6. deletes the temporary topic

The smoke test is implemented in:

```text
infra/kafka/smoke-test.sh
```

This verifies the complete:

```text
producer -> Kafka -> consumer
```

path without requiring OpsSight or ServiceCore application integration.