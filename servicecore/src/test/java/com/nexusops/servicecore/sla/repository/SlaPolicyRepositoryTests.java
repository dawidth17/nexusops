package com.nexusops.servicecore.sla.repository;

import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.sla.domain.SlaCalendarType;
import com.nexusops.servicecore.sla.domain.SlaPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SlaPolicyRepositoryTests {

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Test
    void loadsSeededSlaPolicies() {
        List<SlaPolicy> policies =
                slaPolicyRepository.findAll();

        assertEquals(4, policies.size());
    }

    @Test
    void loadsP1Policy() {
        SlaPolicy policy = slaPolicyRepository
                .findById(Priority.P1)
                .orElseThrow();

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
    void containsPolicyForEveryPriority() {
        for (Priority priority : Priority.values()) {
            assertTrue(
                    slaPolicyRepository.existsById(priority)
            );
        }
    }
}