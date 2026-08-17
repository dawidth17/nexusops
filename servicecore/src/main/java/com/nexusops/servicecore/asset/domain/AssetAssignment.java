package com.nexusops.servicecore.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_assignments")
public class AssetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "asset_id",
            nullable = false
    )
    private Asset asset;

    @Column(
            name = "assignee_id",
            nullable = false,
            length = 255
    )
    private String assigneeId;

    @Column(
            name = "assigned_at",
            nullable = false
    )
    private Instant assignedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    protected AssetAssignment() {
    }

    private AssetAssignment(
            Asset asset,
            String assigneeId,
            Instant assignedAt
    ) {
        if (asset == null) {
            throw new IllegalArgumentException(
                    "asset must not be null"
            );
        }

        this.asset = asset;
        this.assigneeId = requireText(
                assigneeId,
                "assigneeId"
        );

        if (assignedAt == null) {
            throw new IllegalArgumentException(
                    "assignedAt must not be null"
            );
        }

        this.assignedAt = assignedAt;
    }

    public static AssetAssignment create(
            Asset asset,
            String assigneeId,
            Instant assignedAt
    ) {
        return new AssetAssignment(
                asset,
                assigneeId,
                assignedAt
        );
    }

    public void markReturned(Instant returnedAt) {
        if (returnedAt == null) {
            throw new IllegalArgumentException(
                    "returnedAt must not be null"
            );
        }

        if (this.returnedAt != null) {
            throw new InvalidAssetOperationException(
                    "assignment is already closed"
            );
        }

        if (returnedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException(
                    "returnedAt must not be before assignedAt"
            );
        }

        this.returnedAt = returnedAt;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public boolean isActive() {
        return returnedAt == null;
    }
}