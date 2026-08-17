package com.nexusops.servicecore.incident.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentCommentTests {

    @Test
    void createsComment() {
        Incident incident = createIncident();

        IncidentComment comment = IncidentComment.create(
                incident,
                "user-123",
                "The network team is investigating."
        );

        assertEquals(incident, comment.getIncident());
        assertEquals("user-123", comment.getAuthorId());
        assertEquals(
                "The network team is investigating.",
                comment.getContent()
        );
    }

    @Test
    void trimsAuthorAndContent() {
        Incident incident = createIncident();

        IncidentComment comment = IncidentComment.create(
                incident,
                "  user-123  ",
                "  Investigation started.  "
        );

        assertEquals("user-123", comment.getAuthorId());
        assertEquals("Investigation started.", comment.getContent());
    }

    @Test
    void rejectsMissingIncident() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IncidentComment.create(
                        null,
                        "user-123",
                        "Test comment"
                )
        );
    }

    @Test
    void rejectsBlankAuthor() {
        Incident incident = createIncident();

        assertThrows(
                IllegalArgumentException.class,
                () -> IncidentComment.create(
                        incident,
                        " ",
                        "Test comment"
                )
        );
    }

    @Test
    void rejectsBlankContent() {
        Incident incident = createIncident();

        assertThrows(
                IllegalArgumentException.class,
                () -> IncidentComment.create(
                        incident,
                        "user-123",
                        " "
                )
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
