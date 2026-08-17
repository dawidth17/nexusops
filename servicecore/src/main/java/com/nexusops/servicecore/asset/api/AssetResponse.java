package com.nexusops.servicecore.asset.api;

import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String assetTag,
        AssetType type,
        String manufacturer,
        String model,
        String serialNumber,
        AssetStatus status,
        String assigneeId,
        Instant createdAt,
        Instant updatedAt
) {

    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getAssetTag(),
                asset.getType(),
                asset.getManufacturer(),
                asset.getModel(),
                asset.getSerialNumber(),
                asset.getStatus(),
                asset.getAssigneeId(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
