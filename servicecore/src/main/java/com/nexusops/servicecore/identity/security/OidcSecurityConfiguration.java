package com.nexusops.servicecore.identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("oidc")
@EnableMethodSecurity
public class OidcSecurityConfiguration {

    @Bean
    public KeycloakRoleConverter keycloakRoleConverter(
            @Value("${nexusops.security.client-id}")
            String clientId
    ) {
        return new KeycloakRoleConverter(clientId);
    }

    @Bean
    public JwtAuthenticationConverter
            jwtAuthenticationConverter(
                    KeycloakRoleConverter roleConverter
            ) {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                roleConverter
        );

        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        http.csrf(
                csrf -> csrf.disable()
        );

        http.sessionManagement(
                session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
        );

        http.authorizeHttpRequests(
                authorize -> authorize

                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/audit/**"
                        )
                        .hasAnyRole(
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/knowledge-articles/*/publish",
                                "/api/v1/knowledge-articles/*/archive"
                        )
                        .hasAnyRole(
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/assets"
                        )
                        .hasAnyRole(
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/assets/**"
                        )
                        .hasAnyRole(
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/knowledge-articles/**"
                        )
                        .hasAnyRole(
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/knowledge-articles"
                        )
                        .hasAnyRole(
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/incidents"
                        )
                        .hasAnyRole(
                                "EMPLOYEE",
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/incidents/**"
                        )
                        .hasAnyRole(
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/incidents/**"
                        )
                        .hasAnyRole(
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/incidents/**"
                        )
                        .hasAnyRole(
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/**"
                        )
                        .hasAnyRole(
                                "EMPLOYEE",
                                "TECHNICIAN",
                                "MANAGER",
                                "ADMIN"
                        )

                        .anyRequest()
                        .denyAll()
        );

        http.oauth2ResourceServer(
                oauth2 ->
                        oauth2.jwt(
                                jwt ->
                                        jwt.jwtAuthenticationConverter(
                                                jwtAuthenticationConverter
                                        )
                        )
        );

        return http.build();
    }
}