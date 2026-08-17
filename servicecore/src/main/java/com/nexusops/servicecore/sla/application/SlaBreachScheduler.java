package com.nexusops.servicecore.sla.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaBreachScheduler {

    private final SlaBreachService slaBreachService;

    public SlaBreachScheduler(
            SlaBreachService slaBreachService
    ) {
        this.slaBreachService = slaBreachService;
    }

    @Scheduled(
            fixedDelayString =
                    "${nexusops.sla.breach-check-delay-ms:60000}"
    )
    public void checkBreaches() {
        slaBreachService.evaluatePendingBreaches();
    }
}
