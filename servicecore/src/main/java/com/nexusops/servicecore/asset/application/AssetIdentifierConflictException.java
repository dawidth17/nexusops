package com.nexusops.servicecore.asset.application;

public class AssetIdentifierConflictException
        extends RuntimeException {

    public AssetIdentifierConflictException(
            String field,
            String value
    ) {
        super(
                "asset with "
                        + field
                        + " "
                        + value
                        + " already exists"
        );
    }
}