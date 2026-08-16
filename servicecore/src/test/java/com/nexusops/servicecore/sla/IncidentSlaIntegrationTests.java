package com.nexusops.servicecore.sla;

import com.nexusops.servicecore.incident.application.IncidentService;
import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import com.nexusops.servicecore.sla.application.SlaBreachService;
import com.nexusops.servicecore.sla.application.SlaService;
import com.nexusops.servicecore.sla.domain.IncidentSla;
import com.nexusops.servicecore.sla.repository.IncidentSlaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class IncidentSlaIntegrationTests {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private SlaService slaService;

    @Autowired
    private SlaBreachService slaBreachService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentSlaRepository incidentSlaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void keepsIncidentAndSlaLifecycleInSync() {
        Incident incident = incidentService.create(
                "Critical network outage",
                "The internal network is unavailable",
                Impact.HIGH,
                Urgency.HIGH
        );

        assertNotNull(incident.getId());
        assertNotNull(incident.getCreatedAt());

        IncidentSla sla =
                slaService.getByIncidentId(
                        incident.getId()
                );

        assertEquals(
                incident.getCreatedAt()
                        .plus(15, ChronoUnit.MINUTES),
                sla.getFirstResponseDueAt()
        );

        assertEquals(
                incident.getCreatedAt()
                        .plus(240, ChronoUnit.MINUTES),
                sla.getResolutionDueAt()
        );

        incidentService.startProgress(
                incident.getId()
        );

        entityManager.flush();
        entityManager.clear();

        Incident afterResponse = incidentRepository
                .findById(incident.getId())
                .orElseThrow();

        IncidentSla afterResponseSla =
                incidentSlaRepository
                        .findById(incident.getId())
                        .orElseThrow();

        assertEquals(
                IncidentStatus.IN_PROGRESS,
                afterResponse.getStatus()
        );

        assertNotNull(
                afterResponseSla.getFirstRespondedAt()
        );

        incidentService.resolve(
                incident.getId()
        );

        entityManager.flush();
        entityManager.clear();

        Incident resolvedIncident =
                incidentRepository
                        .findById(incident.getId())
                        .orElseThrow();

        IncidentSla resolvedSla =
                incidentSlaRepository
                        .findById(incident.getId())
                        .orElseThrow();

        assertEquals(
                IncidentStatus.RESOLVED,
                resolvedIncident.getStatus()
        );

        assertNotNull(resolvedSla.getResolvedAt());

        incidentService.reopen(
                incident.getId()
        );

        entityManager.flush();
        entityManager.clear();

        Incident reopenedIncident =
                incidentRepository
                        .findById(incident.getId())
                        .orElseThrow();

        IncidentSla reopenedSla =
                incidentSlaRepository
                        .findById(incident.getId())
                        .orElseThrow();

        assertEquals(
                IncidentStatus.IN_PROGRESS,
                reopenedIncident.getStatus()
        );

        assertNull(reopenedSla.getResolvedAt());
    }

    @Test
    void detectsAndPersistsOverdueSlaBreaches() {
        Incident incident =
                incidentRepository.saveAndFlush(
                        Incident.create(
                                "Overdue incident",
                                "Test overdue SLA evaluation",
                                Impact.HIGH,
                                Urgency.HIGH
                        )
                );

        Instant responseDeadline =
                Instant.parse("2020-01-01T10:15:00Z");

        Instant resolutionDeadline =
                Instant.parse("2020-01-01T14:00:00Z");

        IncidentSla sla = IncidentSla.create(
                incident,
                responseDeadline,
                resolutionDeadline
        );

        incidentSlaRepository.saveAndFlush(sla);

        entityManager.clear();

        slaBreachService.evaluatePendingBreaches();

        entityManager.flush();
        entityManager.clear();

        IncidentSla persistedSla =
                incidentSlaRepository
                        .findById(incident.getId())
                        .orElseThrow();

        assertTrue(
                persistedSla.isResponseBreached()
        );

        assertTrue(
                persistedSla.isResolutionBreached()
        );

        assertEquals(
                responseDeadline,
                persistedSla.getResponseBreachedAt()
        );

        assertEquals(
                resolutionDeadline,
                persistedSla.getResolutionBreachedAt()
        );
    }
}