package com.nexusops.servicecore.incident.application;

import java.util.UUID;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(UUID incidentId) {
        super("incident not found: " + incidentId);
    }
}