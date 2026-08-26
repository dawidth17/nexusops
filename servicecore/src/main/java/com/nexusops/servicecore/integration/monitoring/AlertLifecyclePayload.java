package com.nexusops.servicecore.integration.monitoring;

import java.time.Instant;
import java.util.UUID;

public record AlertLifecyclePayload(
        UUID alertId,
        UUID alertRuleId,
        UUID checkId,
        String status,
        String severity,
        String message,
        Instant openedAt,
        Instant recoveredAt
) {
}
