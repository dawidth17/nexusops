package com.nexusops.servicecore.sla.application;

import com.nexusops.servicecore.incident.domain.Priority;

public class SlaPolicyNotFoundException extends RuntimeException {

    public SlaPolicyNotFoundException(Priority priority) {
        super("SLA policy not found for priority " + priority);
    }
}