package com.nexusops.servicecore.identity.api;

import com.nexusops.servicecore.identity.application.CurrentUser;
import com.nexusops.servicecore.identity.domain.SecurityRole;

import java.util.Comparator;
import java.util.List;

public record CurrentUserResponse(
        String subjectId,
        String username,
        List<SecurityRole> roles
) {

    public static CurrentUserResponse from(
            CurrentUser user
    ) {
        List<SecurityRole> roles =
                user.roles()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        Enum::name
                                )
                        )
                        .toList();

        return new CurrentUserResponse(
                user.subjectId(),
                user.username(),
                roles
        );
    }
}
