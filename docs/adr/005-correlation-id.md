# ADR-005: Correlation IDs across NexusOps

## Status

Accepted

## Context

An operational problem can cross SentinelAgent, OpsSight, Kafka, and ServiceCore.

Each component already has its own resource and event identifiers such as telemetry batch IDs, alert IDs, Kafka event IDs, and incident IDs. Those identifiers describe different objects and must not be reused as the cross-component trace identity.

NexusOps needs one correlation identity that can be preserved while an operational workflow crosses component boundaries.

## Decision

NexusOps uses `correlationId` as the cross-component operational trace identity.

An existing correlation ID must always be propagated unchanged.

A component must not replace an existing correlation ID merely because the workflow crossed a service, process, database, or transport boundary.

Correlation IDs follow the shared event-envelope constraints:

- between 1 and 128 characters
- first character is an ASCII letter or digit
- remaining characters may contain letters, digits, `.`, `_`, `:`, or `-`

### SentinelAgent telemetry

SentinelAgent is normally the origin of telemetry and therefore normally has no incoming correlation ID.

For a telemetry envelope SentinelAgent deterministically derives the correlation ID from:

```text
sentinel-agent|<agent_id>|<batch_id>
```

The UTF-8 value is hashed with SHA-256 and represented as a lowercase hexadecimal string.

Because telemetry retries reuse the same batch ID, retransmitting the same batch produces the same correlation ID.

### OpsSight telemetry

`TelemetryEnvelope.correlation_id` carries the correlation identity over gRPC.

OpsSight validates and preserves an incoming correlation ID.

For compatibility with clients that do not send a correlation ID, OpsSight applies the same deterministic SentinelAgent rule:

```text
SHA-256(
    sentinel-agent|<agent_id>|<batch_id>
)
```

The resolved value is stored with the telemetry batch and included in telemetry labels.

If OpsSight receives the same agent and batch ID again with a different correlation ID, the request is rejected instead of silently changing the identity of an already accepted batch.

### OpsSight checks

A check may be started with an existing correlation ID when it belongs to an already correlated workflow.

That incoming value is propagated unchanged.

A standalone check with no incoming correlation ID deterministically derives one from:

```text
opssight-check|<check_id>|<started_at_utc>
```

The UTF-8 value is hashed with SHA-256 and represented as lowercase hexadecimal.

OpsSight does not implicitly attach a check to the most recently received telemetry batch. Such a rule could incorrectly correlate unrelated operational activity. A caller that knows the causal relationship must propagate the existing correlation ID explicitly.

### OpsSight alerts

When a check opens an alert, the alert stores the check correlation ID.

The alert correlation ID remains unchanged for the entire alert lifecycle.

A later successful check may recover the alert, but it does not replace the correlation ID originally stored when the alert opened.

### Kafka

OpsSight copies the alert correlation ID into the common Kafka event envelope:

```json
{
  "correlationId": "..."
}
```

Kafka publication does not generate a new correlation ID.

`eventId` remains the identity of the individual Kafka event.

### ServiceCore

ServiceCore consumes the correlation ID from the OpsSight Kafka event envelope.

Monitoring-sourced incidents persist the same value in `incidents.correlation_id`.

The Kafka event ID, OpsSight alert ID, and ServiceCore incident ID remain independent identifiers.

### Logs

Relevant structured logs include the field:

```text
correlation_id
```

when a correlation identity is known.

This allows one value to be used when searching telemetry processing, alert transitions, Kafka publication, and ServiceCore consumption.

## Consequences

A correlation identity can be preserved across NexusOps component boundaries.

Retries of the same telemetry batch remain attached to the same identity.

Alert recovery remains attached to the alert-opening trace.

Resource IDs and event IDs retain their own independent meanings.

Deterministic fallback creation allows older or standalone callers to participate in correlation without generating a different value during equivalent processing.

Correlation IDs are tracing metadata and are not authentication or authorization credentials.