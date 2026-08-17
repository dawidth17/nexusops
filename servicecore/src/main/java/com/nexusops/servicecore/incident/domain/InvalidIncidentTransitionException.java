package com.nexusops.servicecore.incident.domain;

public class InvalidIncidentTransitionException extends RuntimeException {

    public InvalidIncidentTransitionException(
            IncidentStatus currentStatus,
            IncidentStatus targetStatus
    ) {
        super("cannot transition incident from " + currentStatus + " to " + targetStatus);
    }
}
