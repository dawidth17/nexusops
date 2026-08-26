package com.nexusops.servicecore.integration.monitoring;

import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentSource;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class MonitoringAlertEventProcessorTests {

    @Autowired
    private AlertLifecycleKafkaConsumer consumer;

    @Autowired
    private MonitoringAlertEventProcessor processor;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private ProcessedEventRepository
            processedEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void duplicateKafkaDeliveryCreatesExactlyOneIncident()
            throws JacksonException {

        UUID eventId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();

        String correlationId =
                UUID.randomUUID().toString();

        AlertLifecycleEvent event =
                openedEvent(
                        eventId,
                        alertId,
                        correlationId
                );

        String json =
                jsonMapper.writeValueAsString(
                        event
                );

        consumer.consume(
                json
        );

        consumer.consume(
                json
        );

        assertEquals(
                1L,
                incidentRepository
                        .countBySourceAndSourceAlertId(
                                IncidentSource.MONITORING,
                                alertId
                        )
        );

        Incident incident =
                incidentRepository
                        .findBySourceAndSourceAlertId(
                                IncidentSource.MONITORING,
                                alertId
                        )
                        .orElseThrow();

        assertEquals(
                IncidentSource.MONITORING,
                incident.getSource()
        );

        assertEquals(
                alertId,
                incident.getSourceAlertId()
        );

        assertEquals(
                correlationId,
                incident.getCorrelationId()
        );

        assertEquals(
                IncidentStatus.OPEN,
                incident.getStatus()
        );

        assertTrue(
                processedEventRepository.existsById(
                        eventId
                )
        );
    }

    @Test
    void differentOpenedEventIdsForSameAlertStillCreateOneIncident() {
        UUID alertId = UUID.randomUUID();

        String correlationId =
                UUID.randomUUID().toString();

        UUID firstEventId =
                UUID.randomUUID();

        UUID secondEventId =
                UUID.randomUUID();

        assertEquals(
                EventProcessingResult.PROCESSED,
                processor.process(
                        openedEvent(
                                firstEventId,
                                alertId,
                                correlationId
                        )
                )
        );

        assertEquals(
                EventProcessingResult.PROCESSED,
                processor.process(
                        openedEvent(
                                secondEventId,
                                alertId,
                                correlationId
                        )
                )
        );

        assertEquals(
                1L,
                incidentRepository
                        .countBySourceAndSourceAlertId(
                                IncidentSource.MONITORING,
                                alertId
                        )
        );

        assertTrue(
                processedEventRepository.existsById(
                        firstEventId
                )
        );

        assertTrue(
                processedEventRepository.existsById(
                        secondEventId
                )
        );
    }

    @Test
    void recoveredEventAddsContextWithoutClosingIncident() {
        UUID alertId = UUID.randomUUID();

        String correlationId =
                UUID.randomUUID().toString();

        processor.process(
                openedEvent(
                        UUID.randomUUID(),
                        alertId,
                        correlationId
                )
        );

        Incident incident =
                incidentRepository
                        .findBySourceAndSourceAlertId(
                                IncidentSource.MONITORING,
                                alertId
                        )
                        .orElseThrow();

        incident.assignToUser(
                "technician-001"
        );

        incident.startProgress();

        Instant recoveredAt =
                Instant.parse(
                        "2026-08-26T12:30:00Z"
                );

        UUID recoveryEventId =
                UUID.randomUUID();

        AlertLifecycleEvent recoveryEvent =
                recoveredEvent(
                        recoveryEventId,
                        alertId,
                        correlationId,
                        recoveredAt
                );

        assertEquals(
                EventProcessingResult.PROCESSED,
                processor.process(
                        recoveryEvent
                )
        );

        Incident recoveredIncident =
                incidentRepository
                        .findBySourceAndSourceAlertId(
                                IncidentSource.MONITORING,
                                alertId
                        )
                        .orElseThrow();

        assertEquals(
                IncidentStatus.IN_PROGRESS,
                recoveredIncident.getStatus()
        );

        assertEquals(
                "technician-001",
                recoveredIncident.getAssigneeId()
        );

        assertEquals(
                recoveredAt,
                recoveredIncident
                        .getMonitoringRecoveredAt()
        );

        assertEquals(
                "HTTP check recovered",
                recoveredIncident
                        .getMonitoringRecoveryMessage()
        );

        assertTrue(
                processedEventRepository.existsById(
                        recoveryEventId
                )
        );
    }

    @Test
    void unsupportedEventIsNotRecordedAsProcessed() {
        UUID eventId = UUID.randomUUID();

        AlertLifecycleEvent event =
                new AlertLifecycleEvent(
                        eventId,
                        "nexusops.alert.invalid",
                        1,
                        Instant.now(),
                        "opssight",
                        UUID.randomUUID().toString(),
                        new AlertLifecyclePayload(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "open",
                                "critical",
                                "invalid event",
                                Instant.now(),
                                null
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> processor.process(
                        event
                )
        );

        assertFalse(
                processedEventRepository.existsById(
                        eventId
                )
        );
    }

    private AlertLifecycleEvent openedEvent(
            UUID eventId,
            UUID alertId,
            String correlationId
    ) {
        Instant openedAt =
                Instant.parse(
                        "2026-08-26T10:00:00Z"
                );

        return new AlertLifecycleEvent(
                eventId,
                "nexusops.alert.opened",
                1,
                openedAt,
                "opssight",
                correlationId,
                new AlertLifecyclePayload(
                        alertId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "open",
                        "critical",
                        "HTTP check failed with status 503",
                        openedAt,
                        null
                )
        );
    }

    private AlertLifecycleEvent recoveredEvent(
            UUID eventId,
            UUID alertId,
            String correlationId,
            Instant recoveredAt
    ) {
        return new AlertLifecycleEvent(
                eventId,
                "nexusops.alert.recovered",
                1,
                recoveredAt,
                "opssight",
                correlationId,
                new AlertLifecyclePayload(
                        alertId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "recovered",
                        "critical",
                        "HTTP check recovered",
                        Instant.parse(
                                "2026-08-26T10:00:00Z"
                        ),
                        recoveredAt
                )
        );
    }
}
