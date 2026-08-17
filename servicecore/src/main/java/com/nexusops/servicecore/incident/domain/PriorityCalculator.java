package com.nexusops.servicecore.incident.domain;

import java.util.Objects;

public final class PriorityCalculator {

    private PriorityCalculator() {
    }

    public static Priority calculate(Impact impact, Urgency urgency) {
        Objects.requireNonNull(impact, "impact must not be null");
        Objects.requireNonNull(urgency, "urgency must not be null");

        return switch (impact) {
            case LOW -> switch (urgency) {
                case LOW, MEDIUM -> Priority.P4;
                case HIGH -> Priority.P3;
            };
            case MEDIUM -> switch (urgency) {
                case LOW -> Priority.P4;
                case MEDIUM -> Priority.P3;
                case HIGH -> Priority.P2;
            };
            case HIGH -> switch (urgency) {
                case LOW -> Priority.P3;
                case MEDIUM -> Priority.P2;
                case HIGH -> Priority.P1;
            };
        };
    }
}
