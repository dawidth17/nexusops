package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import com.nexusops.servicecore.sla.application.SlaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-08-17T10:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private SlaService slaService;

    @Test
    void createsIncidentAndSla() {
        IncidentService service = createService();

        when(incidentRepository.saveAndFlush(any(Incident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Incident incident = service.create(
                "Network issue",
                "Users cannot access internal services",
                Impact.HIGH,
                Urgency.HIGH
        );

        assertEquals("Network issue", incident.getTitle());
        assertEquals(
                "Users cannot access internal services",
                incident.getDescription()
        );
        assertEquals(Impact.HIGH, incident.getImpact());
        assertEquals(Urgency.HIGH, incident.getUrgency());
        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(
                IncidentStatus.OPEN,
                incident.getStatus()
        );

        verify(incidentRepository)
                .saveAndFlush(incident);

        verify(slaService)
                .createForIncident(incident);
    }

    @Test
    void getsIncidentById() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();
        Incident incident = createIncident();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = service.getById(incidentId);

        assertEquals(incident, result);

        verify(incidentRepository)
                .findById(incidentId);
    }

    @Test
    void rejectsMissingIncident() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.empty());

        assertThrows(
                IncidentNotFoundException.class,
                () -> service.getById(incidentId)
        );
    }

    @Test
    void searchesIncidents() {
        IncidentService service = createService();

        IncidentSearchCriteria criteria =
                new IncidentSearchCriteria(
                        IncidentStatus.OPEN,
                        Priority.P2,
                        Impact.MEDIUM,
                        Urgency.HIGH,
                        "network-operations",
                        "user-123",
                        "network"
                );

        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        Incident incident = createIncident();

        Page<Incident> expectedPage = new PageImpl<>(
                List.of(incident),
                pageable,
                1
        );

        when(
                incidentRepository.findAll(
                        any(Specification.class),
                        eq(pageable)
                )
        ).thenReturn(expectedPage);

        Page<Incident> result = service.search(
                criteria,
                pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(
                incident,
                result.getContent().get(0)
        );

        verify(incidentRepository).findAll(
                any(Specification.class),
                eq(pageable)
        );
    }

    @Test
    void rejectsNullSearchCriteria() {
        IncidentService service = createService();

        Pageable pageable = PageRequest.of(0, 20);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.search(
                        null,
                        pageable
                )
        );

        assertEquals(
                "criteria must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(incidentRepository);
    }

    @Test
    void rejectsNullPageable() {
        IncidentService service = createService();

        IncidentSearchCriteria criteria =
                new IncidentSearchCriteria(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.search(
                        criteria,
                        null
                )
        );

        assertEquals(
                "pageable must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(incidentRepository);
    }

    @Test
    void updatesIncidentAssessment() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();
        Incident incident = createIncident();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = service.updateAssessment(
                incidentId,
                Impact.HIGH,
                Urgency.HIGH
        );

        assertEquals(Impact.HIGH, result.getImpact());
        assertEquals(Urgency.HIGH, result.getUrgency());
        assertEquals(Priority.P1, result.getPriority());
    }

    @Test
    void assignsIncidentToTeam() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();
        Incident incident = createIncident();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = service.assignToTeam(
                incidentId,
                "network-operations"
        );

        assertEquals(
                "network-operations",
                result.getTeamId()
        );
    }

    @Test
    void assignsIncidentToUser() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();
        Incident incident = createIncident();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = service.assignToUser(
                incidentId,
                "user-123"
        );

        assertEquals(
                "user-123",
                result.getAssigneeId()
        );
    }

    @Test
    void clearsIncidentAssignee() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.assignToUser("user-123");

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result =
                service.clearAssignee(incidentId);

        assertTrue(result.getAssigneeId() == null);
    }

    @Test
    void clearsIncidentTeam() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.assignToTeam("network-operations");

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result =
                service.clearTeam(incidentId);

        assertTrue(result.getTeamId() == null);
    }

    @Test
    void startsProgressAndRecordsFirstResponse() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();
        Incident incident = createIncident();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result =
                service.startProgress(incidentId);

        assertEquals(
                IncidentStatus.IN_PROGRESS,
                result.getStatus()
        );

        verify(slaService).markFirstResponse(
                incidentId,
                NOW
        );
    }

    @Test
    void returnsIncidentToOpen() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.startProgress();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result =
                service.returnToOpen(incidentId);

        assertEquals(
                IncidentStatus.OPEN,
                result.getStatus()
        );
    }

    @Test
    void resolvesIncidentAndRecordsResolution() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.startProgress();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result =
                service.resolve(incidentId);

        assertEquals(
                IncidentStatus.RESOLVED,
                result.getStatus()
        );

        verify(slaService).markResolved(
                incidentId,
                NOW
        );
    }

    @Test
    void reopensIncidentAndSla() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.startProgress();
        incident.resolve();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result =
                service.reopen(incidentId);

        assertEquals(
                IncidentStatus.IN_PROGRESS,
                result.getStatus()
        );

        verify(slaService)
                .markReopened(incidentId);
    }

    @Test
    void closesIncident() {
        IncidentService service = createService();

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.startProgress();
        incident.resolve();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result =
                service.close(incidentId);

        assertEquals(
                IncidentStatus.CLOSED,
                result.getStatus()
        );
    }

    private IncidentService createService() {
        return new IncidentService(
                incidentRepository,
                slaService,
                CLOCK
        );
    }

    private Incident createIncident() {
        return Incident.create(
                "Network issue",
                "Users cannot access internal services",
                Impact.MEDIUM,
                Urgency.MEDIUM
        );
    }
}