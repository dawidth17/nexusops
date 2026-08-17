package com.nexusops.servicecore.identity.security;

import com.nexusops.servicecore.identity.application.CurrentUser;
import com.nexusops.servicecore.identity.application.CurrentUserProvider;
import com.nexusops.servicecore.identity.domain.SecurityRole;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("dev")
public class DevCurrentUserProvider
        implements CurrentUserProvider {

    private static final CurrentUser DEV_USER =
            new CurrentUser(
                    "dev-user",
                    "developer",
                    Set.of(SecurityRole.ADMIN)
            );

    @Override
    public CurrentUser currentUser() {
        return DEV_USER;
    }
}