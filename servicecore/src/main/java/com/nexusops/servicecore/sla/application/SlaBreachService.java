package com.nexusops.servicecore.sla.application;

import com.nexusops.servicecore.sla.domain.IncidentSla;
import com.nexusops.servicecore.sla.repository.IncidentSlaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class SlaBreachService {

    private static final int BATCH_SIZE = 100;

    private final IncidentSlaRepository incidentSlaRepository;
    private final Clock clock;

    public SlaBreachService(
            IncidentSlaRepository incidentSlaRepository,
            Clock clock
    ) {
        this.incidentSlaRepository = incidentSlaRepository;
        this.clock = clock;
    }

    @Transactional
    public void evaluatePendingBreaches() {
        Instant now = clock.instant();

        evaluatePendingResponseBreaches(now);
        evaluatePendingResolutionBreaches(now);
    }

    private void evaluatePendingResponseBreaches(
            Instant now
    ) {
        while (true) {
            List<IncidentSla> slas =
                    incidentSlaRepository
                            .findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                                    now,
                                    PageRequest.of(
                                            0,
                                            BATCH_SIZE
                                    )
                            );

            if (slas.isEmpty()) {
                return;
            }

            slas.forEach(
                    sla -> sla.evaluateBreaches(now)
            );

            incidentSlaRepository.flush();
        }
    }

    private void evaluatePendingResolutionBreaches(
            Instant now
    ) {
        while (true) {
            List<IncidentSla> slas =
                    incidentSlaRepository
                            .findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                                    now,
                                    PageRequest.of(
                                            0,
                                            BATCH_SIZE
                                    )
                            );

            if (slas.isEmpty()) {
                return;
            }

            slas.forEach(
                    sla -> sla.evaluateBreaches(now)
            );

            incidentSlaRepository.flush();
        }
    }
}