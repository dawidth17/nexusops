package com.nexusops.servicecore.integration.monitoring;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AlertLifecycleKafkaConsumer {

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

        processor.process(
                event
        );
    }
}
