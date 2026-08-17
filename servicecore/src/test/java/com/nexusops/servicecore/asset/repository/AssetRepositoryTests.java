package com.nexusops.servicecore.asset.repository;

import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AssetRepositoryTests {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsAsset() {
        Asset asset = Asset.create(
                "LAP-TEST-001",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 7450",
                "SERIAL-TEST-001"
        );

        Asset savedAsset =
                assetRepository.saveAndFlush(asset);

        assertNotNull(savedAsset.getId());
        assertNotNull(savedAsset.getCreatedAt());
        assertNotNull(savedAsset.getUpdatedAt());

        entityManager.clear();

        Asset loadedAsset = assetRepository
                .findById(savedAsset.getId())
                .orElseThrow();

        assertEquals(
                "LAP-TEST-001",
                loadedAsset.getAssetTag()
        );

        assertEquals(
                AssetType.LAPTOP,
                loadedAsset.getType()
        );

        assertEquals(
                AssetStatus.AVAILABLE,
                loadedAsset.getStatus()
        );

        assertNull(loadedAsset.getAssigneeId());
    }

    @Test
    void persistsAssignmentAndReturn() {
        Asset asset = assetRepository.saveAndFlush(
                Asset.create(
                        "LAP-TEST-002",
                        AssetType.LAPTOP,
                        "Lenovo",
                        "ThinkPad T14",
                        "SERIAL-TEST-002"
                )
        );

        asset.assignTo("user-123");

        assetRepository.flush();
        entityManager.clear();

        Asset assignedAsset = assetRepository
                .findById(asset.getId())
                .orElseThrow();

        assertEquals(
                AssetStatus.ASSIGNED,
                assignedAsset.getStatus()
        );

        assertEquals(
                "user-123",
                assignedAsset.getAssigneeId()
        );

        assignedAsset.returnAsset();

        assetRepository.flush();
        entityManager.clear();

        Asset returnedAsset = assetRepository
                .findById(asset.getId())
                .orElseThrow();

        assertEquals(
                AssetStatus.AVAILABLE,
                returnedAsset.getStatus()
        );

        assertNull(returnedAsset.getAssigneeId());
    }

    @Test
    void findsExistingIdentifiers() {
        assetRepository.saveAndFlush(
                Asset.create(
                        "LAP-TEST-003",
                        AssetType.LAPTOP,
                        "HP",
                        "EliteBook",
                        "SERIAL-TEST-003"
                )
        );

        assertTrue(
                assetRepository.existsByAssetTag(
                        "LAP-TEST-003"
                )
        );

        assertTrue(
                assetRepository.existsBySerialNumber(
                        "SERIAL-TEST-003"
                )
        );
    }
}