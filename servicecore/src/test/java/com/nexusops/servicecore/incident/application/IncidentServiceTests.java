package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
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

    @Mock
    private IncidentRepository incidentRepository;

    @Test
    void createsIncident() {
        IncidentService service = new IncidentService(
                incidentRepository
        );

        when(incidentRepository.save(any(Incident.class)))
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
        assertEquals(IncidentStatus.OPEN, incident.getStatus());

        verify(incidentRepository).save(incident);
    }

    @Test
    void getsIncidentById() {
        IncidentService service = new IncidentService(
                incidentRepository
        );

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = service.getById(incidentId);

        assertEquals(incident, result);

        verify(incidentRepository).findById(incidentId);
    }

    @Test
    void rejectsMissingIncident() {
        IncidentService service = new IncidentService(
                incidentRepository
        );

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
        IncidentService service = new IncidentService(
                incidentRepository
        );

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
        IncidentService service = new IncidentService(
                incidentRepository
        );

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
        IncidentService service = new IncidentService(
                incidentRepository
        );

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
        IncidentService service = new IncidentService(
                incidentRepository
        );

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
        IncidentService service = new IncidentService(
                incidentRepository
        );

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
        IncidentService service = new IncidentService(
                incidentRepository
        );

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
        IncidentService service = new IncidentService(
                incidentRepository
        );

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.assignToUser("user-123");

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = service.clearAssignee(
                incidentId
        );

        assertTrue(result.getAssigneeId() == null);
    }

    @Test
    void clearsIncidentTeam() {
        IncidentService service = new IncidentService(
                incidentRepository
        );

        UUID incidentId = UUID.randomUUID();

        Incident incident = createIncident();
        incident.assignToTeam("network-operations");

        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        Incident result = service.clearTeam(
                incidentId
        );

        assertTrue(result.getTeamId() == null);
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