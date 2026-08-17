package com.nexusops.servicecore.asset.application;

import java.util.UUID;

public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException(UUID assetId) {
        super("asset not found: " + assetId);
    }
}