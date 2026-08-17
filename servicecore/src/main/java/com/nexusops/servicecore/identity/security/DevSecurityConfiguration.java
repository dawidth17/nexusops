package com.nexusops.servicecore.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
@EnableWebSecurity
public class DevSecurityConfiguration {

    @Bean
    public SecurityFilterChain devSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http.csrf(
                csrf -> csrf.disable()
        );

        http.authorizeHttpRequests(
                authorize ->
                        authorize
                                .anyRequest()
                                .permitAll()
        );

        return http.build();
    }
}
