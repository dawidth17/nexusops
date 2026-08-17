package com.nexusops.servicecore.asset.application;

import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetAssignment;
import com.nexusops.servicecore.asset.domain.AssetType;
import com.nexusops.servicecore.asset.repository.AssetAssignmentRepository;
import com.nexusops.servicecore.asset.repository.AssetRepository;
import com.nexusops.servicecore.asset.repository.AssetSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetAssignmentRepository assignmentRepository;
    private final Clock clock;

    public AssetService(
            AssetRepository assetRepository,
            AssetAssignmentRepository assignmentRepository,
            Clock clock
    ) {
        this.assetRepository = assetRepository;
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
    }

    public Asset create(
            String assetTag,
            AssetType type,
            String manufacturer,
            String model,
            String serialNumber
    ) {
        String normalizedAssetTag = assetTag == null
                ? null
                : assetTag.trim();

        String normalizedSerialNumber =
                serialNumber == null
                        ? null
                        : serialNumber.trim();

        if (
                normalizedAssetTag != null
                        && assetRepository.existsByAssetTag(
                                normalizedAssetTag
                        )
        ) {
            throw new AssetIdentifierConflictException(
                    "asset tag",
                    normalizedAssetTag
            );
        }

        if (
                normalizedSerialNumber != null
                        && assetRepository.existsBySerialNumber(
                                normalizedSerialNumber
                        )
        ) {
            throw new AssetIdentifierConflictException(
                    "serial number",
                    normalizedSerialNumber
            );
        }

        Asset asset = Asset.create(
                assetTag,
                type,
                manufacturer,
                model,
                serialNumber
        );

        return assetRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public Asset getById(UUID assetId) {
        return findAsset(assetId);
    }

    @Transactional(readOnly = true)
    public Page<Asset> search(
            AssetSearchCriteria criteria,
            Pageable pageable
    ) {
        Specification<Asset> specification =
                Specification.unrestricted();

        specification = specification
                .and(
                        AssetSpecifications.hasType(
                                criteria.type()
                        )
                )
                .and(
                        AssetSpecifications.hasStatus(
                                criteria.status()
                        )
                )
                .and(
                        AssetSpecifications.hasAssigneeId(
                                criteria.assigneeId()
                        )
                )
                .and(
                        AssetSpecifications.hasManufacturer(
                                criteria.manufacturer()
                        )
                )
                .and(
                        AssetSpecifications.containsText(
                                criteria.query()
                        )
                );

        return assetRepository.findAll(
                specification,
                pageable
        );
    }

    public Asset assign(
            UUID assetId,
            String assigneeId
    ) {
        Asset asset = findAsset(assetId);

        asset.assignTo(assigneeId);

        AssetAssignment assignment =
                AssetAssignment.create(
                        asset,
                        asset.getAssigneeId(),
                        clock.instant()
                );

        assignmentRepository.save(assignment);

        return asset;
    }

    public Asset returnAsset(UUID assetId) {
        Asset asset = findAsset(assetId);

        AssetAssignment assignment =
                assignmentRepository
                        .findByAsset_IdAndReturnedAtIsNull(
                                assetId
                        )
                        .orElseThrow(
                                () ->
                                        new AssetAssignmentNotFoundException(
                                                assetId
                                        )
                        );

        asset.returnAsset();

        assignment.markReturned(
                clock.instant()
        );

        return asset;
    }

    public Asset sendToMaintenance(UUID assetId) {
        Asset asset = findAsset(assetId);

        asset.sendToMaintenance();

        return asset;
    }

    public Asset returnFromMaintenance(UUID assetId) {
        Asset asset = findAsset(assetId);

        asset.returnFromMaintenance();

        return asset;
    }

    public Asset retire(UUID assetId) {
        Asset asset = findAsset(assetId);

        asset.retire();

        return asset;
    }

    @Transactional(readOnly = true)
    public List<AssetAssignment> getAssignmentHistory(
            UUID assetId
    ) {
        findAsset(assetId);

        return assignmentRepository
                .findByAsset_IdOrderByAssignedAtDesc(
                        assetId
                );
    }

    private Asset findAsset(UUID assetId) {
        return assetRepository
                .findById(assetId)
                .orElseThrow(
                        () -> new AssetNotFoundException(
                                assetId
                        )
                );
    }
}