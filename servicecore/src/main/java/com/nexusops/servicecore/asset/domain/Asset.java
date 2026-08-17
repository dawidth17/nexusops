package com.nexusops.servicecore.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            name = "asset_tag",
            nullable = false,
            unique = true,
            length = 100
    )
    private String assetTag;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 50
    )
    private AssetType type;

    @Column(
            nullable = false,
            length = 100
    )
    private String manufacturer;

    @Column(
            nullable = false,
            length = 150
    )
    private String model;

    @Column(
            name = "serial_number",
            nullable = false,
            unique = true,
            length = 150
    )
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private AssetStatus status;

    @Column(
            name = "assignee_id",
            length = 255
    )
    private String assigneeId;

    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected Asset() {
    }

    private Asset(
            String assetTag,
            AssetType type,
            String manufacturer,
            String model,
            String serialNumber
    ) {
        this.assetTag = requireText(
                assetTag,
                "assetTag"
        );

        if (type == null) {
            throw new IllegalArgumentException(
                    "type must not be null"
            );
        }

        this.type = type;

        this.manufacturer = requireText(
                manufacturer,
                "manufacturer"
        );

        this.model = requireText(
                model,
                "model"
        );

        this.serialNumber = requireText(
                serialNumber,
                "serialNumber"
        );

        status = AssetStatus.AVAILABLE;
    }

    public static Asset create(
            String assetTag,
            AssetType type,
            String manufacturer,
            String model,
            String serialNumber
    ) {
        return new Asset(
                assetTag,
                type,
                manufacturer,
                model,
                serialNumber
        );
    }

    public void assignTo(String assigneeId) {
        if (status != AssetStatus.AVAILABLE) {
            throw new InvalidAssetOperationException(
                    "only available assets can be assigned"
            );
        }

        this.assigneeId = requireText(
                assigneeId,
                "assigneeId"
        );

        status = AssetStatus.ASSIGNED;
    }

    public void returnAsset() {
        if (status != AssetStatus.ASSIGNED) {
            throw new InvalidAssetOperationException(
                    "only assigned assets can be returned"
            );
        }

        assigneeId = null;
        status = AssetStatus.AVAILABLE;
    }

    public void sendToMaintenance() {
        if (status != AssetStatus.AVAILABLE) {
            throw new InvalidAssetOperationException(
                    "only available assets can enter maintenance"
            );
        }

        status = AssetStatus.MAINTENANCE;
    }

    public void returnFromMaintenance() {
        if (status != AssetStatus.MAINTENANCE) {
            throw new InvalidAssetOperationException(
                    "asset is not in maintenance"
            );
        }

        status = AssetStatus.AVAILABLE;
    }

    public void retire() {
        if (status == AssetStatus.ASSIGNED) {
            throw new InvalidAssetOperationException(
                    "assigned assets must be returned before retirement"
            );
        }

        if (status == AssetStatus.RETIRED) {
            return;
        }

        assigneeId = null;
        status = AssetStatus.RETIRED;
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

    public String getAssetTag() {
        return assetTag;
    }

    public AssetType getType() {
        return type;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
