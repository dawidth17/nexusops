package com.nexusops.servicecore.sla.application;

import java.util.UUID;

public class IncidentSlaNotFoundException extends RuntimeException {

    public IncidentSlaNotFoundException(UUID incidentId) {
        super("SLA not found for incident " + incidentId);
    }
}
