package com.nexusops.servicecore.audit.api;

import com.nexusops.servicecore.audit.domain.AuditAction;
import com.nexusops.servicecore.audit.domain.AuditEntityType;
import com.nexusops.servicecore.audit.domain.AuditEntry;

import java.time.Instant;
import java.util.UUID;

public record AuditResponse(
        UUID id,
        String actorId,
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        String beforeSummary,
        String afterSummary,
        Instant occurredAt,
        UUID correlationId
) {

    public static AuditResponse from(
            AuditEntry entry
    ) {
        return new AuditResponse(
                entry.getId(),
                entry.getActorId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getBeforeSummary(),
                entry.getAfterSummary(),
                entry.getOccurredAt(),
                entry.getCorrelationId()
        );
    }
}