package com.nexusops.servicecore.integration.monitoring;

import java.time.Instant;
import java.util.UUID;

public record AlertLifecycleEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String source,
        String correlationId,
        AlertLifecyclePayload payload
) {
}
