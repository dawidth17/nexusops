package com.nexusops.servicecore.asset.application;

import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;

public record AssetSearchCriteria(
        AssetType type,
        AssetStatus status,
        String assigneeId,
        String manufacturer,
        String query
) {
}
