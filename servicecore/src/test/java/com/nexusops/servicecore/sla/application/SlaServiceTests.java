package com.nexusops.servicecore.sla.application;

import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.sla.domain.IncidentSla;
import com.nexusops.servicecore.sla.domain.SlaCalendarType;
import com.nexusops.servicecore.sla.domain.SlaPolicy;
import com.nexusops.servicecore.sla.repository.IncidentSlaRepository;
import com.nexusops.servicecore.sla.repository.SlaPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaServiceTests {

    @Mock
    private SlaPolicyRepository slaPolicyRepository;

    @Mock
    private IncidentSlaRepository incidentSlaRepository;

    @Test
    void createsSlaForIncident() {
        SlaService service = createService();

        Incident incident = mock(Incident.class);

        Instant createdAt =
                Instant.parse("2026-08-17T10:00:00Z");

        when(incident.getPriority())
                .thenReturn(Priority.P1);

        when(incident.getCreatedAt())
                .thenReturn(createdAt);

        SlaPolicy policy = new SlaPolicy(
                Priority.P1,
                15,
                240,
                SlaCalendarType.TWENTY_FOUR_SEVEN
        );

        when(slaPolicyRepository.findById(Priority.P1))
                .thenReturn(Optional.of(policy));

        when(incidentSlaRepository.save(any(IncidentSla.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IncidentSla sla = service.createForIncident(incident);

        assertEquals(
                incident,
                sla.getIncident()
        );

        assertEquals(
                Instant.parse("2026-08-17T10:15:00Z"),
                sla.getFirstResponseDueAt()
        );

        assertEquals(
                Instant.parse("2026-08-17T14:00:00Z"),
                sla.getResolutionDueAt()
        );

        verify(slaPolicyRepository)
                .findById(Priority.P1);

        verify(incidentSlaRepository)
                .save(sla);
    }

    @Test
    void rejectsMissingIncident() {
        SlaService service = createService();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createForIncident(null)
        );

        verifyNoInteractions(slaPolicyRepository);
        verifyNoInteractions(incidentSlaRepository);
    }

    @Test
    void rejectsIncidentWithoutCreationTime() {
        SlaService service = createService();

        Incident incident = mock(Incident.class);

        when(incident.getCreatedAt())
                .thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createForIncident(incident)
        );

        verifyNoInteractions(slaPolicyRepository);
        verifyNoInteractions(incidentSlaRepository);
    }

    @Test
    void rejectsMissingSlaPolicy() {
        SlaService service = createService();

        Incident incident = mock(Incident.class);

        when(incident.getPriority())
                .thenReturn(Priority.P1);

        when(incident.getCreatedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-17T10:00:00Z"
                        )
                );

        when(slaPolicyRepository.findById(Priority.P1))
                .thenReturn(Optional.empty());

        assertThrows(
                SlaPolicyNotFoundException.class,
                () -> service.createForIncident(incident)
        );

        verify(slaPolicyRepository)
                .findById(Priority.P1);

        verifyNoInteractions(incidentSlaRepository);
    }

    @Test
    void getsSlaByIncidentId() {
        SlaService service = createService();

        UUID incidentId = UUID.randomUUID();
        IncidentSla sla = createIncidentSla();

        when(incidentSlaRepository.findById(incidentId))
                .thenReturn(Optional.of(sla));

        IncidentSla result =
                service.getByIncidentId(incidentId);

        assertEquals(sla, result);

        verify(incidentSlaRepository)
                .findById(incidentId);
    }

    @Test
    void marksFirstResponse() {
        SlaService service = createService();

        UUID incidentId = UUID.randomUUID();
        IncidentSla sla = createIncidentSla();

        when(incidentSlaRepository.findById(incidentId))
                .thenReturn(Optional.of(sla));

        Instant respondedAt =
                Instant.parse("2026-08-17T10:20:00Z");

        IncidentSla result = service.markFirstResponse(
                incidentId,
                respondedAt
        );

        assertEquals(
                respondedAt,
                result.getFirstRespondedAt()
        );

        assertTrue(result.isResponseBreached());

        assertEquals(
                Instant.parse("2026-08-17T10:15:00Z"),
                result.getResponseBreachedAt()
        );
    }

    @Test
    void marksIncidentResolved() {
        SlaService service = createService();

        UUID incidentId = UUID.randomUUID();
        IncidentSla sla = createIncidentSla();

        when(incidentSlaRepository.findById(incidentId))
                .thenReturn(Optional.of(sla));

        Instant resolvedAt =
                Instant.parse("2026-08-17T13:30:00Z");

        IncidentSla result = service.markResolved(
                incidentId,
                resolvedAt
        );

        assertEquals(
                resolvedAt,
                result.getResolvedAt()
        );
    }

    @Test
    void marksIncidentReopened() {
        SlaService service = createService();

        UUID incidentId = UUID.randomUUID();
        IncidentSla sla = createIncidentSla();

        sla.markResolved(
                Instant.parse("2026-08-17T13:30:00Z")
        );

        when(incidentSlaRepository.findById(incidentId))
                .thenReturn(Optional.of(sla));

        IncidentSla result =
                service.markReopened(incidentId);

        assertNull(result.getResolvedAt());
    }

    @Test
    void rejectsMissingIncidentSla() {
        SlaService service = createService();

        UUID incidentId = UUID.randomUUID();

        when(incidentSlaRepository.findById(incidentId))
                .thenReturn(Optional.empty());

        assertThrows(
                IncidentSlaNotFoundException.class,
                () -> service.getByIncidentId(incidentId)
        );
    }

    private SlaService createService() {
        return new SlaService(
                slaPolicyRepository,
                incidentSlaRepository
        );
    }

    private IncidentSla createIncidentSla() {
        Incident incident = mock(Incident.class);

        return IncidentSla.create(
                incident,
                Instant.parse("2026-08-17T10:15:00Z"),
                Instant.parse("2026-08-17T14:00:00Z")
        );
    }
}
