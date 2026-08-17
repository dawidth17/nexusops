package com.nexusops.servicecore.asset.api;

import com.nexusops.servicecore.asset.domain.AssetAssignment;

import java.time.Instant;
import java.util.UUID;

public record AssetAssignmentResponse(
        UUID id,
        UUID assetId,
        String assigneeId,
        Instant assignedAt,
        Instant returnedAt,
        boolean active
) {

    public static AssetAssignmentResponse from(
            AssetAssignment assignment,
            UUID assetId
    ) {
        return new AssetAssignmentResponse(
                assignment.getId(),
                assetId,
                assignment.getAssigneeId(),
                assignment.getAssignedAt(),
                assignment.getReturnedAt(),
                assignment.isActive()
        );
    }
}
