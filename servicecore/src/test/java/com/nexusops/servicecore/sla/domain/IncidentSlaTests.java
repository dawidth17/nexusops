package com.nexusops.servicecore.sla.domain;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.Urgency;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentSlaTests {

    @Test
    void createsIncidentSla() {
        Incident incident = createIncident();

        Instant responseDueAt =
                Instant.parse("2026-08-17T10:15:00Z");

        Instant resolutionDueAt =
                Instant.parse("2026-08-17T14:00:00Z");

        IncidentSla sla = IncidentSla.create(
                incident,
                responseDueAt,
                resolutionDueAt
        );

        assertEquals(incident, sla.getIncident());
        assertEquals(
                responseDueAt,
                sla.getFirstResponseDueAt()
        );
        assertEquals(
                resolutionDueAt,
                sla.getResolutionDueAt()
        );

        assertNull(sla.getFirstRespondedAt());
        assertNull(sla.getResolvedAt());
        assertFalse(sla.isResponseBreached());
        assertFalse(sla.isResolutionBreached());
    }

    @Test
    void recordsResponseBeforeDeadlineWithoutBreach() {
        IncidentSla sla = createSla();

        Instant respondedAt =
                Instant.parse("2026-08-17T10:10:00Z");

        sla.markFirstResponse(respondedAt);

        assertEquals(
                respondedAt,
                sla.getFirstRespondedAt()
        );
        assertFalse(sla.isResponseBreached());
        assertNull(sla.getResponseBreachedAt());
    }

    @Test
    void recordsResponseAfterDeadlineAsBreached() {
        IncidentSla sla = createSla();

        sla.markFirstResponse(
                Instant.parse("2026-08-17T10:20:00Z")
        );

        assertTrue(sla.isResponseBreached());

        assertEquals(
                Instant.parse("2026-08-17T10:15:00Z"),
                sla.getResponseBreachedAt()
        );
    }

    @Test
    void keepsFirstResponseTime() {
        IncidentSla sla = createSla();

        Instant firstResponse =
                Instant.parse("2026-08-17T10:05:00Z");

        sla.markFirstResponse(firstResponse);

        sla.markFirstResponse(
                Instant.parse("2026-08-17T10:12:00Z")
        );

        assertEquals(
                firstResponse,
                sla.getFirstRespondedAt()
        );
    }

    @Test
    void recordsResolutionBeforeDeadlineWithoutBreach() {
        IncidentSla sla = createSla();

        Instant resolvedAt =
                Instant.parse("2026-08-17T13:30:00Z");

        sla.markResolved(resolvedAt);

        assertEquals(
                resolvedAt,
                sla.getResolvedAt()
        );
        assertFalse(sla.isResolutionBreached());
        assertNull(sla.getResolutionBreachedAt());
    }

    @Test
    void recordsResolutionAfterDeadlineAsBreached() {
        IncidentSla sla = createSla();

        sla.markResolved(
                Instant.parse("2026-08-17T14:30:00Z")
        );

        assertTrue(sla.isResolutionBreached());

        assertEquals(
                Instant.parse("2026-08-17T14:00:00Z"),
                sla.getResolutionBreachedAt()
        );
    }

    @Test
    void detectsResponseBreachDuringEvaluation() {
        IncidentSla sla = createSla();

        sla.evaluateBreaches(
                Instant.parse("2026-08-17T10:16:00Z")
        );

        assertTrue(sla.isResponseBreached());

        assertEquals(
                Instant.parse("2026-08-17T10:15:00Z"),
                sla.getResponseBreachedAt()
        );

        assertFalse(sla.isResolutionBreached());
    }

    @Test
    void detectsResolutionBreachDuringEvaluation() {
        IncidentSla sla = createSla();

        sla.markFirstResponse(
                Instant.parse("2026-08-17T10:05:00Z")
        );

        sla.evaluateBreaches(
                Instant.parse("2026-08-17T14:01:00Z")
        );

        assertFalse(sla.isResponseBreached());
        assertTrue(sla.isResolutionBreached());

        assertEquals(
                Instant.parse("2026-08-17T14:00:00Z"),
                sla.getResolutionBreachedAt()
        );
    }

    @Test
    void breachEvaluationIsIdempotent() {
        IncidentSla sla = createSla();

        sla.evaluateBreaches(
                Instant.parse("2026-08-17T15:00:00Z")
        );

        Instant responseBreachedAt =
                sla.getResponseBreachedAt();

        Instant resolutionBreachedAt =
                sla.getResolutionBreachedAt();

        sla.evaluateBreaches(
                Instant.parse("2026-08-17T16:00:00Z")
        );

        assertEquals(
                responseBreachedAt,
                sla.getResponseBreachedAt()
        );

        assertEquals(
                resolutionBreachedAt,
                sla.getResolutionBreachedAt()
        );
    }

    @Test
    void reopeningClearsResolutionTime() {
        IncidentSla sla = createSla();

        sla.markResolved(
                Instant.parse("2026-08-17T13:00:00Z")
        );

        sla.markReopened();

        assertNull(sla.getResolvedAt());
    }

    @Test
    void rejectsMissingIncident() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IncidentSla.create(
                        null,
                        Instant.parse(
                                "2026-08-17T10:15:00Z"
                        ),
                        Instant.parse(
                                "2026-08-17T14:00:00Z"
                        )
                )
        );
    }

    @Test
    void rejectsResolutionDeadlineBeforeResponseDeadline() {
        Incident incident = createIncident();

        assertThrows(
                IllegalArgumentException.class,
                () -> IncidentSla.create(
                        incident,
                        Instant.parse(
                                "2026-08-17T14:00:00Z"
                        ),
                        Instant.parse(
                                "2026-08-17T10:15:00Z"
                        )
                )
        );
    }

    private IncidentSla createSla() {
        return IncidentSla.create(
                createIncident(),
                Instant.parse("2026-08-17T10:15:00Z"),
                Instant.parse("2026-08-17T14:00:00Z")
        );
    }

    private Incident createIncident() {
        return Incident.create(
                "Network outage",
                "Users cannot access internal services",
                Impact.HIGH,
                Urgency.HIGH
        );
    }
}