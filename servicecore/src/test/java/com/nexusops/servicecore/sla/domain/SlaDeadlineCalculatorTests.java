package com.nexusops.servicecore.sla.domain;

import com.nexusops.servicecore.incident.domain.Priority;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaDeadlineCalculatorTests {

    private final SlaDeadlineCalculator calculator =
            new SlaDeadlineCalculator();

    @Test
    void calculatesTwentyFourSevenDeadlines() {
        Instant startedAt =
                Instant.parse("2026-08-17T10:00:00Z");

        SlaPolicy policy = new SlaPolicy(
                Priority.P1,
                15,
                240,
                SlaCalendarType.TWENTY_FOUR_SEVEN
        );

        SlaDeadlines deadlines = calculator.calculate(
                startedAt,
                policy
        );

        assertEquals(
                Instant.parse("2026-08-17T10:15:00Z"),
                deadlines.firstResponseDueAt()
        );

        assertEquals(
                Instant.parse("2026-08-17T14:00:00Z"),
                deadlines.resolutionDueAt()
        );
    }

    @Test
    void calculatesDeadlinesAcrossDayBoundary() {
        Instant startedAt =
                Instant.parse("2026-08-17T22:00:00Z");

        SlaPolicy policy = new SlaPolicy(
                Priority.P3,
                240,
                1440,
                SlaCalendarType.TWENTY_FOUR_SEVEN
        );

        SlaDeadlines deadlines = calculator.calculate(
                startedAt,
                policy
        );

        assertEquals(
                Instant.parse("2026-08-18T02:00:00Z"),
                deadlines.firstResponseDueAt()
        );

        assertEquals(
                Instant.parse("2026-08-18T22:00:00Z"),
                deadlines.resolutionDueAt()
        );
    }

    @Test
    void rejectsMissingStartTime() {
        SlaPolicy policy = new SlaPolicy(
                Priority.P1,
                15,
                240,
                SlaCalendarType.TWENTY_FOUR_SEVEN
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        null,
                        policy
                )
        );
    }

    @Test
    void rejectsMissingPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        Instant.parse(
                                "2026-08-17T10:00:00Z"
                        ),
                        null
                )
        );
    }
}
