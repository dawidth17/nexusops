package com.nexusops.servicecore.identity.security;

import com.nexusops.servicecore.identity.application.CurrentUser;
import com.nexusops.servicecore.identity.application.CurrentUserProvider;
import com.nexusops.servicecore.identity.domain.SecurityRole;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@Profile("oidc")
public class JwtCurrentUserProvider
        implements CurrentUserProvider {

    @Override
    public CurrentUser currentUser() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (
                !(authentication
                        instanceof JwtAuthenticationToken jwtAuthentication)
                        || !authentication.isAuthenticated()
        ) {
            throw new IllegalStateException(
                    "authenticated JWT user not available"
            );
        }

        String subjectId =
                jwtAuthentication
                        .getToken()
                        .getSubject();

        String username =
                jwtAuthentication
                        .getToken()
                        .getClaimAsString(
                                "preferred_username"
                        );

        Set<SecurityRole> roles =
                new LinkedHashSet<>();

        for (
                GrantedAuthority authority
                        : jwtAuthentication.getAuthorities()
        ) {
            SecurityRole
                    .fromValue(
                            authority.getAuthority()
                    )
                    .ifPresent(roles::add);
        }

        return new CurrentUser(
                subjectId,
                username,
                roles
        );
    }
}
