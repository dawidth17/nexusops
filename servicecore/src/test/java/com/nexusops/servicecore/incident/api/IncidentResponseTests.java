package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentSource;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentResponseTests {

    @Test
    void mapsMonitoringIncidentContext() {
        UUID incidentId = UUID.randomUUID();
        UUID sourceAlertId = UUID.randomUUID();

        String correlationId =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        Instant recoveredAt =
                Instant.parse(
                        "2026-08-27T08:00:00Z"
                );

        Instant createdAt =
                Instant.parse(
                        "2026-08-27T07:00:00Z"
                );

        Instant updatedAt =
                Instant.parse(
                        "2026-08-27T08:00:00Z"
                );

        Incident incident =
                mock(Incident.class);

        when(incident.getId())
                .thenReturn(incidentId);

        when(incident.getTitle())
                .thenReturn(
                        "Monitoring incident"
                );

        when(incident.getDescription())
                .thenReturn(
                        "Created from OpsSight"
                );

        when(incident.getImpact())
                .thenReturn(Impact.HIGH);

        when(incident.getUrgency())
                .thenReturn(Urgency.HIGH);

        when(incident.getPriority())
                .thenReturn(Priority.P1);

        when(incident.getStatus())
                .thenReturn(
                        IncidentStatus.OPEN
                );

        when(incident.getSource())
                .thenReturn(
                        IncidentSource.MONITORING
                );

        when(incident.getSourceAlertId())
                .thenReturn(sourceAlertId);

        when(incident.getCorrelationId())
                .thenReturn(correlationId);

        when(
                incident
                        .getMonitoringRecoveredAt()
        ).thenReturn(recoveredAt);

        when(
                incident
                        .getMonitoringRecoveryMessage()
        ).thenReturn(
                "Monitoring recovered"
        );

        when(incident.getAssigneeId())
                .thenReturn(null);

        when(incident.getTeamId())
                .thenReturn(
                        "network-operations"
                );

        when(incident.getCreatedAt())
                .thenReturn(createdAt);

        when(incident.getUpdatedAt())
                .thenReturn(updatedAt);

        IncidentResponse response =
                IncidentResponse.from(
                        incident
                );

        assertEquals(
                incidentId,
                response.id()
        );

        assertEquals(
                IncidentSource.MONITORING,
                response.source()
        );

        assertEquals(
                sourceAlertId,
                response.sourceAlertId()
        );

        assertEquals(
                correlationId,
                response.correlationId()
        );

        assertEquals(
                recoveredAt,
                response.monitoringRecoveredAt()
        );

        assertEquals(
                "Monitoring recovered",
                response.monitoringRecoveryMessage()
        );

        assertEquals(
                "network-operations",
                response.teamId()
        );
    }
}
