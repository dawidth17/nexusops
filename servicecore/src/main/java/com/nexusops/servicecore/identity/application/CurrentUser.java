package com.nexusops.servicecore.identity.application;

import com.nexusops.servicecore.identity.domain.SecurityRole;

import java.util.Set;

public record CurrentUser(
        String subjectId,
        String username,
        Set<SecurityRole> roles
) {

    public CurrentUser {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException(
                    "subjectId must not be blank"
            );
        }

        subjectId = subjectId.trim();

        if (username == null || username.isBlank()) {
            username = subjectId;
        } else {
            username = username.trim();
        }

        roles = roles == null
                ? Set.of()
                : Set.copyOf(roles);
    }

    public boolean hasRole(SecurityRole role) {
        return roles.contains(role);
    }
}
