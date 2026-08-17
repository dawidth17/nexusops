package com.nexusops.servicecore;

import com.nexusops.servicecore.identity.security.DevSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(DevSecurityConfiguration.class)
public class ServiceCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                ServiceCoreApplication.class,
                args
        );
    }
}