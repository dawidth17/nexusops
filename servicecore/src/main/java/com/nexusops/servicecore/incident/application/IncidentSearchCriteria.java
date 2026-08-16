package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;

public record IncidentSearchCriteria(
        IncidentStatus status,
        Priority priority,
        Impact impact,
        Urgency urgency,
        String teamId,
        String assigneeId,
        String text
) {
}