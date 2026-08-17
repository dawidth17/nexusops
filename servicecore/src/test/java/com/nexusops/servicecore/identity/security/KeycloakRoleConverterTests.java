package com.nexusops.servicecore.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRoleConverterTests {

    @Test
    void mapsRealmRoles() {
        KeycloakRoleConverter converter =
                new KeycloakRoleConverter(
                        "servicecore"
                );

        Jwt jwt = jwt(
                Map.of(
                        "realm_access",
                        Map.of(
                                "roles",
                                List.of(
                                        "employee",
                                        "technician",
                                        "offline_access"
                                )
                        )
                )
        );

        Set<String> authorities =
                authorities(
                        converter.convert(jwt)
                );

        assertTrue(
                authorities.contains(
                        "ROLE_EMPLOYEE"
                )
        );

        assertTrue(
                authorities.contains(
                        "ROLE_TECHNICIAN"
                )
        );

        assertFalse(
                authorities.contains(
                        "ROLE_OFFLINE_ACCESS"
                )
        );
    }

    @Test
    void mapsOnlyConfiguredClientRoles() {
        KeycloakRoleConverter converter =
                new KeycloakRoleConverter(
                        "servicecore"
                );

        Jwt jwt = jwt(
                Map.of(
                        "resource_access",
                        Map.of(
                                "servicecore",
                                Map.of(
                                        "roles",
                                        List.of(
                                                "technician",
                                                "manager"
                                        )
                                ),
                                "other-client",
                                Map.of(
                                        "roles",
                                        List.of("admin")
                                )
                        )
                )
        );

        Set<String> authorities =
                authorities(
                        converter.convert(jwt)
                );

        assertEquals(
                Set.of(
                        "ROLE_TECHNICIAN",
                        "ROLE_MANAGER"
                ),
                authorities
        );
    }

    @Test
    void combinesRealmAndClientRolesWithoutDuplicates() {
        KeycloakRoleConverter converter =
                new KeycloakRoleConverter(
                        "servicecore"
                );

        Jwt jwt = jwt(
                Map.of(
                        "realm_access",
                        Map.of(
                                "roles",
                                List.of(
                                        "employee",
                                        "manager"
                                )
                        ),
                        "resource_access",
                        Map.of(
                                "servicecore",
                                Map.of(
                                        "roles",
                                        List.of(
                                                "manager",
                                                "admin"
                                        )
                                )
                        )
                )
        );

        Set<String> authorities =
                authorities(
                        converter.convert(jwt)
                );

        assertEquals(
                Set.of(
                        "ROLE_EMPLOYEE",
                        "ROLE_MANAGER",
                        "ROLE_ADMIN"
                ),
                authorities
        );
    }

    private Jwt jwt(
            Map<String, Object> claims
    ) {
        Jwt.Builder builder =
                Jwt.withTokenValue("token")
                        .header(
                                "alg",
                                "none"
                        )
                        .subject("user-123");

        claims.forEach(
                builder::claim
        );

        return builder.build();
    }

    private Set<String> authorities(
            Collection<GrantedAuthority> authorities
    ) {
        return authorities
                .stream()
                .map(
                        GrantedAuthority::getAuthority
                )
                .collect(
                        Collectors.toSet()
                );
    }
}