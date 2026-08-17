package com.nexusops.servicecore.identity.security;

import com.nexusops.servicecore.identity.domain.SecurityRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeycloakRoleConverter
        implements Converter<
                Jwt,
                Collection<GrantedAuthority>
        > {

    private final String clientId;

    public KeycloakRoleConverter(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "clientId must not be blank"
            );
        }

        this.clientId = clientId.trim();
    }

    @Override
    public Collection<GrantedAuthority> convert(
            Jwt jwt
    ) {
        Set<SecurityRole> roles =
                new LinkedHashSet<>();

        collectRealmRoles(
                jwt,
                roles
        );

        collectClientRoles(
                jwt,
                roles
        );

        List<GrantedAuthority> authorities =
                new ArrayList<>();

        for (SecurityRole role : roles) {
            authorities.add(
                    new SimpleGrantedAuthority(
                            role.authority()
                    )
            );
        }

        return List.copyOf(authorities);
    }

    private void collectRealmRoles(
            Jwt jwt,
            Set<SecurityRole> roles
    ) {
        Object realmAccess =
                jwt.getClaim("realm_access");

        if (!(realmAccess instanceof Map<?, ?> map)) {
            return;
        }

        collectRoles(
                map.get("roles"),
                roles
        );
    }

    private void collectClientRoles(
            Jwt jwt,
            Set<SecurityRole> roles
    ) {
        Object resourceAccess =
                jwt.getClaim("resource_access");

        if (!(resourceAccess instanceof Map<?, ?> resources)) {
            return;
        }

        Object clientAccess =
                resources.get(clientId);

        if (!(clientAccess instanceof Map<?, ?> client)) {
            return;
        }

        collectRoles(
                client.get("roles"),
                roles
        );
    }

    private void collectRoles(
            Object rawRoles,
            Set<SecurityRole> roles
    ) {
        if (!(rawRoles instanceof Collection<?> values)) {
            return;
        }

        for (Object value : values) {
            if (!(value instanceof String roleName)) {
                continue;
            }

            SecurityRole
                    .fromValue(roleName)
                    .ifPresent(roles::add);
        }
    }
}