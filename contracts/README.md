# Contracts

Shared contracts between NexusOps components are versioned and stored in this directory.

## REST

REST APIs use versioned paths:

```text
/api/v1/...
```

Breaking API changes require a new version or an explicit migration.

OpenAPI definitions are stored in `openapi/`.

## Protobuf

Protobuf packages include their version.

Example:

```text
nexusops.telemetry.v1
```

Existing Protobuf field numbers must not be reused.

Protobuf definitions are stored in `protobuf/`.

## Events

Events use a common envelope containing:

* `eventId`
* `eventType`
* `schemaVersion`
* `occurredAt`
* `source`
* `correlationId`
* `payload`

Event schemas are stored in `events/`.

## General rules

* timestamps use UTC;
* `correlationId` is propagated across related operations;
* breaking contract changes must be versioned;
* secrets are never included in contracts or examples.
