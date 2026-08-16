package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTests {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentService incidentService;

    @Test
    void createsIncident() {
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Incident incident = incidentService.create(
                "Email service unavailable",
                "Multiple users cannot access email",
                Impact.HIGH,
                Urgency.HIGH
        );

        assertEquals("Email service unavailable", incident.getTitle());
        assertEquals(Impact.HIGH, incident.getImpact());
        assertEquals(Urgency.HIGH, incident.getUrgency());
        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(IncidentStatus.OPEN, incident.getStatus());

        verify(incidentRepository).save(incident);
    }

    @Test
    void getsIncidentById() {
        UUID incidentId = UUID.randomUUID();

        Incident incident = Incident.create(
                "VPN unavailable",
                "Users cannot connect to the VPN",
                Impact.MEDIUM,
                Urgency.HIGH
        );

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = incidentService.getById(incidentId);

        assertEquals(incident, result);

        verify(incidentRepository).findById(incidentId);
    }

    @Test
    void throwsWhenIncidentDoesNotExist() {
        UUID incidentId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.empty());

        assertThrows(
                IncidentNotFoundException.class,
                () -> incidentService.getById(incidentId)
        );

        verify(incidentRepository).findById(incidentId);
    }

    @Test
    void resolvesIncident() {
        UUID incidentId = UUID.randomUUID();

        Incident incident = Incident.create(
                "VPN unavailable",
                "Users cannot connect to the VPN",
                Impact.MEDIUM,
                Urgency.HIGH
        );

        incident.startProgress();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = incidentService.resolve(incidentId);

        assertEquals(IncidentStatus.RESOLVED, result.getStatus());

        verify(incidentRepository).findById(incidentId);
    }
}