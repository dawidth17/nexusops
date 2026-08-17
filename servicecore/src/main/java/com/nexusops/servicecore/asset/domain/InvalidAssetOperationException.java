package com.nexusops.servicecore.asset.domain;

public class InvalidAssetOperationException
        extends RuntimeException {

    public InvalidAssetOperationException(String message) {
        super(message);
    }
}
