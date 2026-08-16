package com.nexusops.servicecore.sla.domain;

import com.nexusops.servicecore.incident.domain.Incident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_slas")
public class IncidentSla {

    @Id
    @Column(name = "incident_id")
    private UUID incidentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(
            name = "first_response_due_at",
            nullable = false
    )
    private Instant firstResponseDueAt;

    @Column(
            name = "resolution_due_at",
            nullable = false
    )
    private Instant resolutionDueAt;

    @Column(name = "first_responded_at")
    private Instant firstRespondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "response_breached_at")
    private Instant responseBreachedAt;

    @Column(name = "resolution_breached_at")
    private Instant resolutionBreachedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IncidentSla() {
    }

    private IncidentSla(
            Incident incident,
            Instant firstResponseDueAt,
            Instant resolutionDueAt
    ) {
        if (incident == null) {
            throw new IllegalArgumentException(
                    "incident must not be null"
            );
        }

        if (firstResponseDueAt == null) {
            throw new IllegalArgumentException(
                    "firstResponseDueAt must not be null"
            );
        }

        if (resolutionDueAt == null) {
            throw new IllegalArgumentException(
                    "resolutionDueAt must not be null"
            );
        }

        if (resolutionDueAt.isBefore(firstResponseDueAt)) {
            throw new IllegalArgumentException(
                    "resolution deadline must not be before "
                            + "first response deadline"
            );
        }

        this.incident = incident;
        this.firstResponseDueAt = firstResponseDueAt;
        this.resolutionDueAt = resolutionDueAt;
    }

    public static IncidentSla create(
            Incident incident,
            Instant firstResponseDueAt,
            Instant resolutionDueAt
    ) {
        return new IncidentSla(
                incident,
                firstResponseDueAt,
                resolutionDueAt
        );
    }

    public void markFirstResponse(Instant respondedAt) {
        requireInstant(respondedAt, "respondedAt");

        if (firstRespondedAt != null) {
            return;
        }

        firstRespondedAt = respondedAt;

        if (
                respondedAt.isAfter(firstResponseDueAt)
                        && responseBreachedAt == null
        ) {
            responseBreachedAt = firstResponseDueAt;
        }
    }

    public void markResolved(Instant resolvedAt) {
        requireInstant(resolvedAt, "resolvedAt");

        if (this.resolvedAt != null) {
            return;
        }

        this.resolvedAt = resolvedAt;

        if (
                resolvedAt.isAfter(resolutionDueAt)
                        && resolutionBreachedAt == null
        ) {
            resolutionBreachedAt = resolutionDueAt;
        }
    }

    public void markReopened() {
        resolvedAt = null;
    }

    public void evaluateBreaches(Instant now) {
        requireInstant(now, "now");

        if (
                firstRespondedAt == null
                        && responseBreachedAt == null
                        && now.isAfter(firstResponseDueAt)
        ) {
            responseBreachedAt = firstResponseDueAt;
        }

        if (
                resolvedAt == null
                        && resolutionBreachedAt == null
                        && now.isAfter(resolutionDueAt)
        ) {
            resolutionBreachedAt = resolutionDueAt;
        }
    }

    public boolean isResponseBreached() {
        return responseBreachedAt != null;
    }

    public boolean isResolutionBreached() {
        return resolutionBreachedAt != null;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    private static void requireInstant(
            Instant value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null"
            );
        }
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public Incident getIncident() {
        return incident;
    }

    public Instant getFirstResponseDueAt() {
        return firstResponseDueAt;
    }

    public Instant getResolutionDueAt() {
        return resolutionDueAt;
    }

    public Instant getFirstRespondedAt() {
        return firstRespondedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getResponseBreachedAt() {
        return responseBreachedAt;
    }

    public Instant getResolutionBreachedAt() {
        return resolutionBreachedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}