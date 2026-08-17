package com.nexusops.servicecore.asset.application;

import java.util.UUID;

public class AssetAssignmentNotFoundException
        extends RuntimeException {

    public AssetAssignmentNotFoundException(
            UUID assetId
    ) {
        super(
                "active assignment not found for asset "
                        + assetId
        );
    }
}
