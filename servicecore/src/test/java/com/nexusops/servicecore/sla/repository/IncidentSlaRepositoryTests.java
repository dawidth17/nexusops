package com.nexusops.servicecore.sla.repository;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import com.nexusops.servicecore.sla.domain.IncidentSla;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class IncidentSlaRepositoryTests {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentSlaRepository incidentSlaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsIncidentSla() {
        Incident incident = incidentRepository.saveAndFlush(
                createIncident("Network outage")
        );

        IncidentSla sla = IncidentSla.create(
                incident,
                Instant.parse("2026-08-17T10:15:00Z"),
                Instant.parse("2026-08-17T14:00:00Z")
        );

        incidentSlaRepository.saveAndFlush(sla);

        entityManager.clear();

        IncidentSla loadedSla = incidentSlaRepository
                .findById(incident.getId())
                .orElseThrow();

        assertEquals(
                incident.getId(),
                loadedSla.getIncidentId()
        );

        assertEquals(
                incident.getId(),
                loadedSla.getIncident().getId()
        );

        assertEquals(
                Instant.parse("2026-08-17T10:15:00Z"),
                loadedSla.getFirstResponseDueAt()
        );

        assertEquals(
                Instant.parse("2026-08-17T14:00:00Z"),
                loadedSla.getResolutionDueAt()
        );
    }

    @Test
    void findsOnlyPendingResponseBreaches() {
        Instant now =
                Instant.parse("2026-08-17T12:00:00Z");

        Incident overdueIncident = incidentRepository.saveAndFlush(
                createIncident("Overdue response")
        );

        Incident futureIncident = incidentRepository.saveAndFlush(
                createIncident("Future response")
        );

        Incident respondedIncident = incidentRepository.saveAndFlush(
                createIncident("Already responded")
        );

        IncidentSla overdueSla = IncidentSla.create(
                overdueIncident,
                Instant.parse("2026-08-17T11:00:00Z"),
                Instant.parse("2026-08-17T15:00:00Z")
        );

        IncidentSla futureSla = IncidentSla.create(
                futureIncident,
                Instant.parse("2026-08-17T13:00:00Z"),
                Instant.parse("2026-08-17T16:00:00Z")
        );

        IncidentSla respondedSla = IncidentSla.create(
                respondedIncident,
                Instant.parse("2026-08-17T11:00:00Z"),
                Instant.parse("2026-08-17T15:00:00Z")
        );

        respondedSla.markFirstResponse(
                Instant.parse("2026-08-17T10:30:00Z")
        );

        incidentSlaRepository.saveAndFlush(overdueSla);
        incidentSlaRepository.saveAndFlush(futureSla);
        incidentSlaRepository.saveAndFlush(respondedSla);

        entityManager.clear();

        List<IncidentSla> result = incidentSlaRepository
                .findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                        now,
                        PageRequest.of(0, 100)
                );

        assertEquals(1, result.size());
        assertEquals(
                overdueIncident.getId(),
                result.get(0).getIncidentId()
        );
    }

    @Test
    void findsOnlyPendingResolutionBreaches() {
        Instant now =
                Instant.parse("2026-08-17T15:00:00Z");

        Incident overdueIncident = incidentRepository.saveAndFlush(
                createIncident("Overdue resolution")
        );

        Incident futureIncident = incidentRepository.saveAndFlush(
                createIncident("Future resolution")
        );

        Incident resolvedIncident = incidentRepository.saveAndFlush(
                createIncident("Already resolved")
        );

        IncidentSla overdueSla = IncidentSla.create(
                overdueIncident,
                Instant.parse("2026-08-17T10:00:00Z"),
                Instant.parse("2026-08-17T14:00:00Z")
        );

        IncidentSla futureSla = IncidentSla.create(
                futureIncident,
                Instant.parse("2026-08-17T10:00:00Z"),
                Instant.parse("2026-08-17T16:00:00Z")
        );

        IncidentSla resolvedSla = IncidentSla.create(
                resolvedIncident,
                Instant.parse("2026-08-17T10:00:00Z"),
                Instant.parse("2026-08-17T14:00:00Z")
        );

        resolvedSla.markResolved(
                Instant.parse("2026-08-17T13:30:00Z")
        );

        incidentSlaRepository.saveAndFlush(overdueSla);
        incidentSlaRepository.saveAndFlush(futureSla);
        incidentSlaRepository.saveAndFlush(resolvedSla);

        entityManager.clear();

        List<IncidentSla> result = incidentSlaRepository
                .findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                        now,
                        PageRequest.of(0, 100)
                );

        assertEquals(1, result.size());
        assertEquals(
                overdueIncident.getId(),
                result.get(0).getIncidentId()
        );
    }

    @Test
    void ignoresAlreadyRecordedBreaches() {
        Instant now =
                Instant.parse("2026-08-17T15:00:00Z");

        Incident incident = incidentRepository.saveAndFlush(
                createIncident("Already breached")
        );

        IncidentSla sla = IncidentSla.create(
                incident,
                Instant.parse("2026-08-17T10:00:00Z"),
                Instant.parse("2026-08-17T14:00:00Z")
        );

        sla.evaluateBreaches(now);

        incidentSlaRepository.saveAndFlush(sla);

        entityManager.clear();

        List<IncidentSla> responseBreaches =
                incidentSlaRepository
                        .findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                                now,
                                PageRequest.of(0, 100)
                        );

        List<IncidentSla> resolutionBreaches =
                incidentSlaRepository
                        .findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                                now,
                                PageRequest.of(0, 100)
                        );

        assertTrue(responseBreaches.isEmpty());
        assertTrue(resolutionBreaches.isEmpty());

        IncidentSla persisted = incidentSlaRepository
                .findById(incident.getId())
                .orElseThrow();

        assertFalse(
                persisted.getResponseBreachedAt() == null
        );

        assertFalse(
                persisted.getResolutionBreachedAt() == null
        );
    }

    private Incident createIncident(String title) {
        return Incident.create(
                title,
                "Test incident description",
                Impact.MEDIUM,
                Urgency.MEDIUM
        );
    }
}