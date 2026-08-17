package com.nexusops.servicecore.asset.repository;

import com.nexusops.servicecore.asset.domain.AssetAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetAssignmentRepository
        extends JpaRepository<AssetAssignment, UUID> {

    Optional<AssetAssignment>
            findByAsset_IdAndReturnedAtIsNull(
                    UUID assetId
            );

    List<AssetAssignment>
            findByAsset_IdOrderByAssignedAtDesc(
                    UUID assetId
            );
}