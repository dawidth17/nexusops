package com.nexusops.servicecore.integration.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(
            name = "event_type",
            nullable = false,
            length = 200
    )
    private String eventType;

    @Column(
            nullable = false,
            length = 64
    )
    private String source;

    @Column(
            name = "correlation_id",
            nullable = false,
            length = 128
    )
    private String correlationId;

    @Column(
            name = "processed_at",
            nullable = false
    )
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
