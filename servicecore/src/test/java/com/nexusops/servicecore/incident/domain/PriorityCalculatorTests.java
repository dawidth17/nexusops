package com.nexusops.servicecore.incident.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriorityCalculatorTests {

    @Test
    void calculatesPriorityFromImpactAndUrgency() {
        assertEquals(Priority.P4, PriorityCalculator.calculate(Impact.LOW, Urgency.LOW));
        assertEquals(Priority.P4, PriorityCalculator.calculate(Impact.LOW, Urgency.MEDIUM));
        assertEquals(Priority.P3, PriorityCalculator.calculate(Impact.LOW, Urgency.HIGH));

        assertEquals(Priority.P4, PriorityCalculator.calculate(Impact.MEDIUM, Urgency.LOW));
        assertEquals(Priority.P3, PriorityCalculator.calculate(Impact.MEDIUM, Urgency.MEDIUM));
        assertEquals(Priority.P2, PriorityCalculator.calculate(Impact.MEDIUM, Urgency.HIGH));

        assertEquals(Priority.P3, PriorityCalculator.calculate(Impact.HIGH, Urgency.LOW));
        assertEquals(Priority.P2, PriorityCalculator.calculate(Impact.HIGH, Urgency.MEDIUM));
        assertEquals(Priority.P1, PriorityCalculator.calculate(Impact.HIGH, Urgency.HIGH));
    }

    @Test
    void rejectsNullImpact() {
        assertThrows(
                NullPointerException.class,
                () -> PriorityCalculator.calculate(null, Urgency.HIGH)
        );
    }

    @Test
    void rejectsNullUrgency() {
        assertThrows(
                NullPointerException.class,
                () -> PriorityCalculator.calculate(Impact.HIGH, null)
        );
    }
}
