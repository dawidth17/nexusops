package com.nexusops.servicecore.integration.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AlertLifecycleKafkaConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AlertLifecycleKafkaConsumer.class
            );

    private final JsonMapper jsonMapper;

    private final MonitoringAlertEventProcessor processor;

    public AlertLifecycleKafkaConsumer(
            JsonMapper jsonMapper,
            MonitoringAlertEventProcessor processor
    ) {
        this.jsonMapper = jsonMapper;
        this.processor = processor;
    }

    @KafkaListener(
            topics = "${servicecore.kafka.alert-topic}",
            groupId = "${servicecore.kafka.consumer-group}",
            autoStartup = "${servicecore.kafka.enabled:false}"
    )
    public void consume(
            String payload
    ) throws JacksonException {
        AlertLifecycleEvent event =
                jsonMapper.readValue(
                        payload,
                        AlertLifecycleEvent.class
                );

        LOGGER.info(
                "monitoring_alert_event_received "
                        + "event_id={} "
                        + "event_type={} "
                        + "correlation_id={}",
                event.eventId(),
                event.eventType(),
                event.correlationId()
        );

        try {
            EventProcessingResult result =
                    processor.process(
                            event
                    );

            LOGGER.info(
                    "monitoring_alert_event_processed "
                            + "event_id={} "
                            + "event_type={} "
                            + "correlation_id={} "
                            + "result={}",
                    event.eventId(),
                    event.eventType(),
                    event.correlationId(),
                    result
            );

        } catch (RuntimeException exception) {
            LOGGER.error(
                    "monitoring_alert_event_failed "
                            + "event_id={} "
                            + "event_type={} "
                            + "correlation_id={}",
                    event.eventId(),
                    event.eventType(),
                    event.correlationId(),
                    exception
            );

            throw exception;
        }
    }
}
