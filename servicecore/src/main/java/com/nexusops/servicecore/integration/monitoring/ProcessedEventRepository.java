package com.nexusops.servicecore.integration.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO processed_events (
                        event_id,
                        event_type,
                        source,
                        correlation_id,
                        processed_at
                    )
                    VALUES (
                        :eventId,
                        :eventType,
                        :source,
                        :correlationId,
                        :processedAt
                    )
                    ON CONFLICT (event_id)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("eventId")
            UUID eventId,

            @Param("eventType")
            String eventType,

            @Param("source")
            String source,

            @Param("correlationId")
            String correlationId,

            @Param("processedAt")
            Instant processedAt
    );
}
