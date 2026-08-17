package com.nexusops.servicecore.sla.domain;

import java.time.Instant;

public record SlaDeadlines(
        Instant firstResponseDueAt,
        Instant resolutionDueAt
) {

    public SlaDeadlines {
        if (firstResponseDueAt == null) {
            throw new IllegalArgumentException(
                    "firstResponseDueAt must not be null"
            );
        }

        if (resolutionDueAt == null) {
            throw new IllegalArgumentException(
                    "resolutionDueAt must not be null"
            );
        }

        if (resolutionDueAt.isBefore(firstResponseDueAt)) {
            throw new IllegalArgumentException(
                    "resolution deadline must not be before "
                            + "first response deadline"
            );
        }
    }
}
