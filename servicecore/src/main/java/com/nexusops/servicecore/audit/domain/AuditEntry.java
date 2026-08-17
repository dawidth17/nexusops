package com.nexusops.servicecore.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    private UUID id;

    @Column(
            name = "actor_id",
            nullable = false,
            updatable = false,
            length = 255
    )
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            updatable = false,
            length = 100
    )
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "entity_type",
            nullable = false,
            updatable = false,
            length = 50
    )
    private AuditEntityType entityType;

    @Column(
            name = "entity_id",
            nullable = false,
            updatable = false
    )
    private UUID entityId;

    @Column(
            name = "before_summary",
            updatable = false,
            columnDefinition = "TEXT"
    )
    private String beforeSummary;

    @Column(
            name = "after_summary",
            updatable = false,
            columnDefinition = "TEXT"
    )
    private String afterSummary;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private Instant occurredAt;

    @Column(
            name = "correlation_id",
            updatable = false
    )
    private UUID correlationId;

    protected AuditEntry() {
    }

    private AuditEntry(
            String actorId,
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            String beforeSummary,
            String afterSummary,
            Instant occurredAt,
            UUID correlationId
    ) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException(
                    "actorId must not be blank"
            );
        }

        if (action == null) {
            throw new IllegalArgumentException(
                    "action must not be null"
            );
        }

        if (entityType == null) {
            throw new IllegalArgumentException(
                    "entityType must not be null"
            );
        }

        if (entityId == null) {
            throw new IllegalArgumentException(
                    "entityId must not be null"
            );
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "occurredAt must not be null"
            );
        }

        id = UUID.randomUUID();
        this.actorId = actorId.trim();
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeSummary = beforeSummary;
        this.afterSummary = afterSummary;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
    }

    public static AuditEntry create(
            String actorId,
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            String beforeSummary,
            String afterSummary,
            Instant occurredAt,
            UUID correlationId
    ) {
        return new AuditEntry(
                actorId,
                action,
                entityType,
                entityId,
                beforeSummary,
                afterSummary,
                occurredAt,
                correlationId
        );
    }

    public UUID getId() {
        return id;
    }

    public String getActorId() {
        return actorId;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getBeforeSummary() {
        return beforeSummary;
    }

    public String getAfterSummary() {
        return afterSummary;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }
}
