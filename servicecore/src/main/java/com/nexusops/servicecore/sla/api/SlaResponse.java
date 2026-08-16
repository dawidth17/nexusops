package com.nexusops.servicecore.sla.api;

import com.nexusops.servicecore.sla.domain.IncidentSla;

import java.time.Instant;
import java.util.UUID;

public record SlaResponse(
        UUID incidentId,
        Instant firstResponseDueAt,
        Instant resolutionDueAt,
        Instant firstRespondedAt,
        Instant resolvedAt,
        Instant responseBreachedAt,
        Instant resolutionBreachedAt,
        boolean responseBreached,
        boolean resolutionBreached
) {

    public static SlaResponse from(IncidentSla sla) {
        return new SlaResponse(
                sla.getIncidentId(),
                sla.getFirstResponseDueAt(),
                sla.getResolutionDueAt(),
                sla.getFirstRespondedAt(),
                sla.getResolvedAt(),
                sla.getResponseBreachedAt(),
                sla.getResolutionBreachedAt(),
                sla.isResponseBreached(),
                sla.isResolutionBreached()
        );
    }
}