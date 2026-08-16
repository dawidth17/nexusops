package com.nexusops.servicecore.incident.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentTests {

    @Test
    void createsIncidentWithCalculatedPriorityAndOpenStatus() {
        Incident incident = Incident.create(
                "Email service unavailable",
                "Multiple users cannot access email",
                Impact.HIGH,
                Urgency.HIGH
        );

        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertEquals(Priority.P1, incident.getPriority());
    }

    @Test
    void createsIncidentWithoutAssignment() {
        Incident incident = createIncident();

        assertNull(incident.getTeamId());
        assertNull(incident.getAssigneeId());
    }

    @Test
    void recalculatesPriorityWhenAssessmentChanges() {
        Incident incident = Incident.create(
                "Email service degraded",
                "One user reports slow email access",
                Impact.LOW,
                Urgency.LOW
        );

        incident.updateAssessment(Impact.HIGH, Urgency.MEDIUM);

        assertEquals(Impact.HIGH, incident.getImpact());
        assertEquals(Urgency.MEDIUM, incident.getUrgency());
        assertEquals(Priority.P2, incident.getPriority());
    }

    @Test
    void assignsIncidentToTeamAndUser() {
        Incident incident = createIncident();

        incident.assignToTeam("network-operations");
        incident.assignToUser("user-123");

        assertEquals("network-operations", incident.getTeamId());
        assertEquals("user-123", incident.getAssigneeId());
    }

    @Test
    void trimsAssignmentIdentifiers() {
        Incident incident = createIncident();

        incident.assignToTeam("  network-operations  ");
        incident.assignToUser("  user-123  ");

        assertEquals("network-operations", incident.getTeamId());
        assertEquals("user-123", incident.getAssigneeId());
    }

    @Test
    void clearsAssignment() {
        Incident incident = createIncident();

        incident.assignToTeam("network-operations");
        incident.assignToUser("user-123");

        incident.clearAssignee();
        incident.clearTeam();

        assertNull(incident.getAssigneeId());
        assertNull(incident.getTeamId());
    }

    @Test
    void rejectsBlankAssignmentIdentifiers() {
        Incident incident = createIncident();

        assertThrows(
                IllegalArgumentException.class,
                () -> incident.assignToTeam(" ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> incident.assignToUser(" ")
        );
    }

    @Test
    void followsNormalWorkflow() {
        Incident incident = createIncident();

        incident.startProgress();
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

        incident.resolve();
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());

        incident.close();
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
    }

    @Test
    void canReturnFromInProgressToOpen() {
        Incident incident = createIncident();

        incident.startProgress();
        incident.returnToOpen();

        assertEquals(IncidentStatus.OPEN, incident.getStatus());
    }

    @Test
    void canReopenResolvedIncident() {
        Incident incident = createIncident();

        incident.startProgress();
        incident.resolve();
        incident.reopen();

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
    }

    @Test
    void rejectsInvalidTransition() {
        Incident incident = createIncident();

        assertThrows(
                InvalidIncidentTransitionException.class,
                incident::resolve
        );

        assertEquals(IncidentStatus.OPEN, incident.getStatus());
    }

    @Test
    void closedIncidentCannotBeReopened() {
        Incident incident = createIncident();

        incident.startProgress();
        incident.resolve();
        incident.close();

        assertThrows(
                InvalidIncidentTransitionException.class,
                incident::reopen
        );

        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
    }

    @Test
    void rejectsBlankTitle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Incident.create(
                        " ",
                        "Description",
                        Impact.LOW,
                        Urgency.LOW
                )
        );
    }

    private Incident createIncident() {
        return Incident.create(
                "Test incident",
                "Test incident description",
                Impact.MEDIUM,
                Urgency.MEDIUM
        );
    }
}