package com.nexusops.servicecore.sla.application;

import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.sla.domain.IncidentSla;
import com.nexusops.servicecore.sla.repository.IncidentSlaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlaBreachServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-08-17T15:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void evaluatesPendingResponseBreaches() {
        IncidentSlaRepository repository =
                mock(IncidentSlaRepository.class);

        IncidentSla sla = IncidentSla.create(
                mock(Incident.class),
                Instant.parse("2026-08-17T14:00:00Z"),
                Instant.parse("2026-08-17T18:00:00Z")
        );

        PageRequest batch = PageRequest.of(0, 100);

        when(
                repository
                        .findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                                NOW,
                                batch
                        )
        ).thenReturn(
                List.of(sla),
                List.of()
        );

        when(
                repository
                        .findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                                NOW,
                                batch
                        )
        ).thenReturn(List.of());

        SlaBreachService service =
                new SlaBreachService(
                        repository,
                        CLOCK
                );

        service.evaluatePendingBreaches();

        assertTrue(sla.isResponseBreached());

        assertEquals(
                Instant.parse("2026-08-17T14:00:00Z"),
                sla.getResponseBreachedAt()
        );

        assertFalse(sla.isResolutionBreached());

        verify(repository).flush();
    }

    @Test
    void evaluatesPendingResolutionBreaches() {
        IncidentSlaRepository repository =
                mock(IncidentSlaRepository.class);

        IncidentSla sla = IncidentSla.create(
                mock(Incident.class),
                Instant.parse("2026-08-17T10:00:00Z"),
                Instant.parse("2026-08-17T14:00:00Z")
        );

        sla.markFirstResponse(
                Instant.parse("2026-08-17T09:30:00Z")
        );

        PageRequest batch = PageRequest.of(0, 100);

        when(
                repository
                        .findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                                NOW,
                                batch
                        )
        ).thenReturn(List.of());

        when(
                repository
                        .findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                                NOW,
                                batch
                        )
        ).thenReturn(
                List.of(sla),
                List.of()
        );

        SlaBreachService service =
                new SlaBreachService(
                        repository,
                        CLOCK
                );

        service.evaluatePendingBreaches();

        assertFalse(sla.isResponseBreached());
        assertTrue(sla.isResolutionBreached());

        assertEquals(
                Instant.parse("2026-08-17T14:00:00Z"),
                sla.getResolutionBreachedAt()
        );

        verify(repository).flush();
    }

    @Test
    void doesNothingWhenNoBreachesArePending() {
        IncidentSlaRepository repository =
                mock(IncidentSlaRepository.class);

        PageRequest batch = PageRequest.of(0, 100);

        when(
                repository
                        .findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                                NOW,
                                batch
                        )
        ).thenReturn(List.of());

        when(
                repository
                        .findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                                NOW,
                                batch
                        )
        ).thenReturn(List.of());

        SlaBreachService service =
                new SlaBreachService(
                        repository,
                        CLOCK
                );

        service.evaluatePendingBreaches();

        verify(repository)
                .findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                        NOW,
                        batch
                );

        verify(repository)
                .findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                        NOW,
                        batch
                );
    }
}
