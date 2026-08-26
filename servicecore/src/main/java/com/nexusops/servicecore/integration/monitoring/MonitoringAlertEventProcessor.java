package com.nexusops.servicecore.integration.monitoring;

import com.nexusops.servicecore.incident.application.IncidentService;
import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Urgency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
public class MonitoringAlertEventProcessor {

    private static final String ALERT_OPENED =
            "nexusops.alert.opened";

    private static final String ALERT_RECOVERED =
            "nexusops.alert.recovered";

    private static final String OPSSIGHT =
            "opssight";

    private static final int SCHEMA_VERSION = 1;

    private static final int MAX_TITLE_LENGTH = 200;

    private static final String TITLE_PREFIX =
            "OpsSight alert: ";

    private final ProcessedEventRepository
            processedEventRepository;

    private final IncidentService incidentService;

    private final Clock clock;

    public MonitoringAlertEventProcessor(
            ProcessedEventRepository processedEventRepository,
            IncidentService incidentService,
            Clock clock
    ) {
        this.processedEventRepository =
                processedEventRepository;

        this.incidentService =
                incidentService;

        this.clock =
                clock;
    }

    @Transactional
    public EventProcessingResult process(
            AlertLifecycleEvent event
    ) {
        validateEvent(
                event
        );

        int inserted =
                processedEventRepository.insertIfAbsent(
                        event.eventId(),
                        event.eventType(),
                        event.source(),
                        event.correlationId(),
                        clock.instant()
                );

        if (inserted == 0) {
            return EventProcessingResult.DUPLICATE;
        }

        switch (event.eventType()) {
            case ALERT_OPENED ->
                    processOpened(
                            event
                    );

            case ALERT_RECOVERED ->
                    processRecovered(
                            event
                    );

            default ->
                    throw new IllegalArgumentException(
                            "unsupported event type: "
                                    + event.eventType()
                    );
        }

        return EventProcessingResult.PROCESSED;
    }

    private void processOpened(
            AlertLifecycleEvent event
    ) {
        AlertLifecyclePayload payload =
                event.payload();

        if (!"open".equals(payload.status())) {
            throw new IllegalArgumentException(
                    "AlertOpened requires status=open"
            );
        }

        if (payload.recoveredAt() != null) {
            throw new IllegalArgumentException(
                    "AlertOpened requires recoveredAt=null"
            );
        }

        MonitoringAssessment assessment =
                assessmentForSeverity(
                        payload.severity()
                );

        incidentService.createFromMonitoring(
                buildTitle(
                        payload.message()
                ),
                payload.message(),
                assessment.impact(),
                assessment.urgency(),
                payload.alertId(),
                event.correlationId()
        );
    }

    private void processRecovered(
            AlertLifecycleEvent event
    ) {
        AlertLifecyclePayload payload =
                event.payload();

        if (!"recovered".equals(
                payload.status()
        )) {
            throw new IllegalArgumentException(
                    "AlertRecovered requires status=recovered"
            );
        }

        if (payload.recoveredAt() == null) {
            throw new IllegalArgumentException(
                    "AlertRecovered requires recoveredAt"
            );
        }

        assessmentForSeverity(
                payload.severity()
        );

        incidentService.recordMonitoringRecovery(
                payload.alertId(),
                payload.recoveredAt(),
                payload.message()
        );
    }

    private void validateEvent(
            AlertLifecycleEvent event
    ) {
        Objects.requireNonNull(
                event,
                "event must not be null"
        );

        Objects.requireNonNull(
                event.eventId(),
                "eventId must not be null"
        );

        if (
                !ALERT_OPENED.equals(
                        event.eventType()
                )
                && !ALERT_RECOVERED.equals(
                        event.eventType()
                )
        ) {
            throw new IllegalArgumentException(
                    "unsupported event type: "
                            + event.eventType()
            );
        }

        if (event.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported schema version: "
                            + event.schemaVersion()
            );
        }

        Objects.requireNonNull(
                event.occurredAt(),
                "occurredAt must not be null"
        );

        if (!OPSSIGHT.equals(event.source())) {
            throw new IllegalArgumentException(
                    "unexpected event source: "
                            + event.source()
            );
        }

        requireText(
                event.correlationId(),
                "correlationId"
        );

        AlertLifecyclePayload payload =
                Objects.requireNonNull(
                        event.payload(),
                        "payload must not be null"
                );

        Objects.requireNonNull(
                payload.alertId(),
                "alertId must not be null"
        );

        Objects.requireNonNull(
                payload.alertRuleId(),
                "alertRuleId must not be null"
        );

        Objects.requireNonNull(
                payload.checkId(),
                "checkId must not be null"
        );

        Objects.requireNonNull(
                payload.openedAt(),
                "openedAt must not be null"
        );

        requireText(
                payload.message(),
                "message"
        );

        assessmentForSeverity(
                payload.severity()
        );
    }

    private MonitoringAssessment assessmentForSeverity(
            String severity
    ) {
        return switch (
                requireText(
                        severity,
                        "severity"
                )
        ) {
            case "critical" ->
                    new MonitoringAssessment(
                            Impact.HIGH,
                            Urgency.HIGH
                    );

            case "warning" ->
                    new MonitoringAssessment(
                            Impact.MEDIUM,
                            Urgency.MEDIUM
                    );

            case "info" ->
                    new MonitoringAssessment(
                            Impact.LOW,
                            Urgency.LOW
                    );

            default ->
                    throw new IllegalArgumentException(
                            "unsupported alert severity: "
                                    + severity
                    );
        };
    }

    private String buildTitle(
            String message
    ) {
        String normalizedMessage =
                requireText(
                        message,
                        "message"
                );

        int availableLength =
                MAX_TITLE_LENGTH
                        - TITLE_PREFIX.length();

        if (
                normalizedMessage.length()
                        > availableLength
        ) {
            normalizedMessage =
                    normalizedMessage.substring(
                            0,
                            availableLength
                    );
        }

        return TITLE_PREFIX
                + normalizedMessage;
    }

    private String requireText(
            String value,
            String fieldName
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not be blank"
            );
        }

        return value.trim();
    }

    private record MonitoringAssessment(
            Impact impact,
            Urgency urgency
    ) {
    }
}
