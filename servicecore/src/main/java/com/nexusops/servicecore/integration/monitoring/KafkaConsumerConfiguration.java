package com.nexusops.servicecore.integration.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    KafkaConsumerConfiguration.class
            );

    private static final long RETRY_INTERVAL_MS =
            1000L;

    private static final long RETRY_ATTEMPTS =
            2L;

    @Bean
    public CommonErrorHandler
            serviceCoreKafkaErrorHandler() {

        return new DefaultErrorHandler(
                (
                        record,
                        exception
                ) -> LOGGER.error(
                        "kafka_event_processing_exhausted "
                                + "topic={} "
                                + "partition={} "
                                + "offset={} "
                                + "key={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        exception
                ),
                new FixedBackOff(
                        RETRY_INTERVAL_MS,
                        RETRY_ATTEMPTS
                )
        );
    }
}
