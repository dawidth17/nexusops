package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentSource;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String title,
        String description,
        Impact impact,
        Urgency urgency,
        Priority priority,
        IncidentStatus status,
        IncidentSource source,
        UUID sourceAlertId,
        String correlationId,
        Instant monitoringRecoveredAt,
        String monitoringRecoveryMessage,
        String assigneeId,
        String teamId,
        Instant createdAt,
        Instant updatedAt
) {

    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getImpact(),
                incident.getUrgency(),
                incident.getPriority(),
                incident.getStatus(),
                incident.getSource(),
                incident.getSourceAlertId(),
                incident.getCorrelationId(),
                incident.getMonitoringRecoveredAt(),
                incident.getMonitoringRecoveryMessage(),
                incident.getAssigneeId(),
                incident.getTeamId(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
