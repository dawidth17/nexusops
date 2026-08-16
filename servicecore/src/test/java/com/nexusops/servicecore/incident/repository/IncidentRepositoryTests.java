package com.nexusops.servicecore.incident.repository;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class IncidentRepositoryTests {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsIncident() {
        Incident incident = Incident.create(
                "Email service unavailable",
                "Multiple users cannot access email",
                Impact.HIGH,
                Urgency.HIGH
        );

        Incident savedIncident = incidentRepository.saveAndFlush(incident);
        UUID incidentId = savedIncident.getId();

        assertNotNull(incidentId);
        assertNotNull(savedIncident.getCreatedAt());
        assertNotNull(savedIncident.getUpdatedAt());

        entityManager.clear();

        Incident loadedIncident = incidentRepository
                .findById(incidentId)
                .orElseThrow();

        assertEquals("Email service unavailable", loadedIncident.getTitle());
        assertEquals(
                "Multiple users cannot access email",
                loadedIncident.getDescription()
        );
        assertEquals(Impact.HIGH, loadedIncident.getImpact());
        assertEquals(Urgency.HIGH, loadedIncident.getUrgency());
        assertEquals(Priority.P1, loadedIncident.getPriority());
        assertEquals(IncidentStatus.OPEN, loadedIncident.getStatus());
    }

    @Test
    void persistsWorkflowChanges() {
        Incident incident = Incident.create(
                "VPN unavailable",
                "Users cannot connect to the corporate VPN",
                Impact.MEDIUM,
                Urgency.HIGH
        );

        Incident savedIncident = incidentRepository.saveAndFlush(incident);

        savedIncident.startProgress();
        savedIncident.resolve();

        incidentRepository.saveAndFlush(savedIncident);

        UUID incidentId = savedIncident.getId();

        entityManager.clear();

        Incident loadedIncident = incidentRepository
                .findById(incidentId)
                .orElseThrow();

        assertEquals(IncidentStatus.RESOLVED, loadedIncident.getStatus());
        assertEquals(Priority.P2, loadedIncident.getPriority());
        assertTrue(
                loadedIncident.getUpdatedAt()
                        .compareTo(loadedIncident.getCreatedAt()) >= 0
        );
    }
}