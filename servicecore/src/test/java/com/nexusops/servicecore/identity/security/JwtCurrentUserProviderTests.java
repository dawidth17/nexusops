package com.nexusops.servicecore.identity.security;

import com.nexusops.servicecore.identity.application.CurrentUser;
import com.nexusops.servicecore.identity.domain.SecurityRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCurrentUserProviderTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsAuthenticatedUserFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header(
                        "alg",
                        "none"
                )
                .subject("keycloak-user-123")
                .claim(
                        "preferred_username",
                        "alice"
                )
                .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_TECHNICIAN"
                                )
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        authentication
                );

        JwtCurrentUserProvider provider =
                new JwtCurrentUserProvider();

        CurrentUser user =
                provider.currentUser();

        assertEquals(
                "keycloak-user-123",
                user.subjectId()
        );

        assertEquals(
                "alice",
                user.username()
        );

        assertTrue(
                user.roles().contains(
                        SecurityRole.TECHNICIAN
                )
        );
    }

    @Test
    void usesSubjectWhenUsernameIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header(
                        "alg",
                        "none"
                )
                .subject("keycloak-user-123")
                .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_EMPLOYEE"
                                )
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        authentication
                );

        CurrentUser user =
                new JwtCurrentUserProvider()
                        .currentUser();

        assertEquals(
                "keycloak-user-123",
                user.username()
        );
    }

    @Test
    void rejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();

        JwtCurrentUserProvider provider =
                new JwtCurrentUserProvider();

        assertThrows(
                IllegalStateException.class,
                provider::currentUser
        );
    }
}