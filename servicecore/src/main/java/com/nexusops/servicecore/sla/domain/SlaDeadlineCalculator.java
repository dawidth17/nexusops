package com.nexusops.servicecore.sla.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SlaDeadlineCalculator {

    public SlaDeadlines calculate(
            Instant startedAt,
            SlaPolicy policy
    ) {
        if (startedAt == null) {
            throw new IllegalArgumentException(
                    "startedAt must not be null"
            );
        }

        if (policy == null) {
            throw new IllegalArgumentException(
                    "policy must not be null"
            );
        }

        if (
                policy.getCalendarType()
                        != SlaCalendarType.TWENTY_FOUR_SEVEN
        ) {
            throw new IllegalArgumentException(
                    "unsupported SLA calendar type: "
                            + policy.getCalendarType()
            );
        }

        Instant firstResponseDueAt = startedAt.plus(
                policy.getFirstResponseTargetMinutes(),
                ChronoUnit.MINUTES
        );

        Instant resolutionDueAt = startedAt.plus(
                policy.getResolutionTargetMinutes(),
                ChronoUnit.MINUTES
        );

        return new SlaDeadlines(
                firstResponseDueAt,
                resolutionDueAt
        );
    }
}