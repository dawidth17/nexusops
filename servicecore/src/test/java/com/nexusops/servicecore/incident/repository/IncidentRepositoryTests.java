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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertNull(loadedIncident.getAssigneeId());
        assertNull(loadedIncident.getTeamId());
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

    @Test
    void persistsAssignmentChanges() {
        Incident incident = Incident.create(
                "Network connectivity issue",
                "Several users cannot access internal services",
                Impact.MEDIUM,
                Urgency.MEDIUM
        );

        Incident savedIncident = incidentRepository.saveAndFlush(incident);
        UUID incidentId = savedIncident.getId();

        savedIncident.assignToTeam("network-operations");
        savedIncident.assignToUser("user-123");

        incidentRepository.saveAndFlush(savedIncident);

        entityManager.clear();

        Incident assignedIncident = incidentRepository
                .findById(incidentId)
                .orElseThrow();

        assertEquals(
                "network-operations",
                assignedIncident.getTeamId()
        );
        assertEquals(
                "user-123",
                assignedIncident.getAssigneeId()
        );

        assignedIncident.clearTeam();
        assignedIncident.clearAssignee();

        incidentRepository.saveAndFlush(assignedIncident);

        entityManager.clear();

        Incident unassignedIncident = incidentRepository
                .findById(incidentId)
                .orElseThrow();

        assertNull(unassignedIncident.getTeamId());
        assertNull(unassignedIncident.getAssigneeId());
    }
}