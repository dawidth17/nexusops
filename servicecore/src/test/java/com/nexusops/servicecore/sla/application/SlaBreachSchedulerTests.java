package com.nexusops.servicecore.sla.application;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SlaBreachSchedulerTests {

    @Test
    void delegatesBreachEvaluation() {
        SlaBreachService slaBreachService =
                mock(SlaBreachService.class);

        SlaBreachScheduler scheduler =
                new SlaBreachScheduler(
                        slaBreachService
                );

        scheduler.checkBreaches();

        verify(slaBreachService)
                .evaluatePendingBreaches();
    }
}