package com.nexusops.servicecore.audit.application;

import org.springframework.stereotype.Component;

@Component
public class SystemAuditActorProvider
        implements AuditActorProvider {

    @Override
    public String currentActorId() {
        return "system";
    }
}