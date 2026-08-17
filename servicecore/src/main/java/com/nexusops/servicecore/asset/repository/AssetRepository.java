package com.nexusops.servicecore.asset.repository;

import com.nexusops.servicecore.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AssetRepository
        extends JpaRepository<Asset, UUID>,
        JpaSpecificationExecutor<Asset> {

    boolean existsByAssetTag(String assetTag);

    boolean existsBySerialNumber(String serialNumber);
}