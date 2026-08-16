package com.nexusops.servicecore.sla.domain;

import com.nexusops.servicecore.incident.domain.Priority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaPolicyTests {

    @Test
    void createsSlaPolicy() {
        SlaPolicy policy = new SlaPolicy(
                Priority.P1,
                15,
                240,
                SlaCalendarType.TWENTY_FOUR_SEVEN
        );

        assertEquals(
                Priority.P1,
                policy.getPriority()
        );

        assertEquals(
                15,
                policy.getFirstResponseTargetMinutes()
        );

        assertEquals(
                240,
                policy.getResolutionTargetMinutes()
        );

        assertEquals(
                SlaCalendarType.TWENTY_FOUR_SEVEN,
                policy.getCalendarType()
        );
    }

    @Test
    void rejectsMissingPriority() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlaPolicy(
                        null,
                        15,
                        240,
                        SlaCalendarType.TWENTY_FOUR_SEVEN
                )
        );
    }

    @Test
    void rejectsNonPositiveResponseTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlaPolicy(
                        Priority.P1,
                        0,
                        240,
                        SlaCalendarType.TWENTY_FOUR_SEVEN
                )
        );
    }

    @Test
    void rejectsNonPositiveResolutionTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlaPolicy(
                        Priority.P1,
                        15,
                        0,
                        SlaCalendarType.TWENTY_FOUR_SEVEN
                )
        );
    }

    @Test
    void rejectsResolutionTargetShorterThanResponseTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlaPolicy(
                        Priority.P1,
                        60,
                        30,
                        SlaCalendarType.TWENTY_FOUR_SEVEN
                )
        );
    }

    @Test
    void rejectsMissingCalendarType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlaPolicy(
                        Priority.P1,
                        15,
                        240,
                        null
                )
        );
    }
}