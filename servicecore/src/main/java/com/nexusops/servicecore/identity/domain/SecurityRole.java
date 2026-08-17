package com.nexusops.servicecore.identity.domain;

import java.util.Locale;
import java.util.Optional;

public enum SecurityRole {
    EMPLOYEE,
    TECHNICIAN,
    MANAGER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<SecurityRole> fromValue(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized =
                value.trim()
                        .toUpperCase(Locale.ROOT);

        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }

        try {
            return Optional.of(
                    SecurityRole.valueOf(normalized)
            );
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}