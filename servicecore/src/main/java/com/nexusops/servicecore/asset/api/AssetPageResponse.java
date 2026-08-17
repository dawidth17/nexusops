package com.nexusops.servicecore.asset.api;

import com.nexusops.servicecore.asset.domain.Asset;
import org.springframework.data.domain.Page;

import java.util.List;

public record AssetPageResponse(
        List<AssetResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static AssetPageResponse from(
            Page<Asset> assets
    ) {
        return new AssetPageResponse(
                assets.getContent()
                        .stream()
                        .map(AssetResponse::from)
                        .toList(),
                assets.getNumber(),
                assets.getSize(),
                assets.getTotalElements(),
                assets.getTotalPages()
        );
    }
}
