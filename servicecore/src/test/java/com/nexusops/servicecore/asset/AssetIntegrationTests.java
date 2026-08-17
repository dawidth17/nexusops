package com.nexusops.servicecore.asset;

import com.nexusops.servicecore.asset.application.AssetSearchCriteria;
import com.nexusops.servicecore.asset.application.AssetService;
import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetAssignment;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;
import com.nexusops.servicecore.asset.repository.AssetAssignmentRepository;
import com.nexusops.servicecore.asset.repository.AssetRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class AssetIntegrationTests {

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetAssignmentRepository assignmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void keepsAssetLifecycleAndAssignmentHistoryInSync() {
        Asset asset = assetService.create(
                "LAP-INTEGRATION-001",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 7450",
                "INTEGRATION-SERIAL-001"
        );

        entityManager.flush();

        assertNotNull(asset.getId());
        assertEquals(
                AssetStatus.AVAILABLE,
                asset.getStatus()
        );

        assetService.assign(
                asset.getId(),
                "user-123"
        );

        entityManager.flush();
        entityManager.clear();

        Asset assigned = assetRepository
                .findById(asset.getId())
                .orElseThrow();

        assertEquals(
                AssetStatus.ASSIGNED,
                assigned.getStatus()
        );

        assertEquals(
                "user-123",
                assigned.getAssigneeId()
        );

        List<AssetAssignment> activeHistory =
                assignmentRepository
                        .findByAsset_IdOrderByAssignedAtDesc(
                                asset.getId()
                        );

        assertEquals(1, activeHistory.size());
        assertNull(
                activeHistory.getFirst()
                        .getReturnedAt()
        );

        assetService.returnAsset(asset.getId());

        entityManager.flush();
        entityManager.clear();

        Asset returned = assetRepository
                .findById(asset.getId())
                .orElseThrow();

        assertEquals(
                AssetStatus.AVAILABLE,
                returned.getStatus()
        );

        assertNull(returned.getAssigneeId());

        List<AssetAssignment> closedHistory =
                assignmentRepository
                        .findByAsset_IdOrderByAssignedAtDesc(
                                asset.getId()
                        );

        assertEquals(1, closedHistory.size());
        assertNotNull(
                closedHistory.getFirst()
                        .getReturnedAt()
        );

        assertFalse(
                closedHistory.getFirst()
                        .isActive()
        );

        assetService.sendToMaintenance(
                asset.getId()
        );

        entityManager.flush();
        entityManager.clear();

        Asset maintenance = assetRepository
                .findById(asset.getId())
                .orElseThrow();

        assertEquals(
                AssetStatus.MAINTENANCE,
                maintenance.getStatus()
        );

        assetService.returnFromMaintenance(
                asset.getId()
        );

        assetService.retire(
                asset.getId()
        );

        entityManager.flush();
        entityManager.clear();

        Asset retired = assetRepository
                .findById(asset.getId())
                .orElseThrow();

        assertEquals(
                AssetStatus.RETIRED,
                retired.getStatus()
        );
    }

    @Test
    void searchesAssetsWithFiltersAndPagination() {
        assetService.create(
                "LAP-INTEGRATION-002",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 5450",
                "INTEGRATION-SERIAL-002"
        );

        assetService.create(
                "LAP-INTEGRATION-003",
                AssetType.LAPTOP,
                "Lenovo",
                "ThinkPad T14",
                "INTEGRATION-SERIAL-003"
        );

        assetService.create(
                "SRV-INTEGRATION-001",
                AssetType.SERVER,
                "Dell",
                "PowerEdge R760",
                "INTEGRATION-SERIAL-004"
        );

        entityManager.flush();
        entityManager.clear();

        AssetSearchCriteria criteria =
                new AssetSearchCriteria(
                        AssetType.LAPTOP,
                        AssetStatus.AVAILABLE,
                        null,
                        null,
                        "integration"
                );

        Page<Asset> page = assetService.search(
                criteria,
                PageRequest.of(
                        0,
                        1,
                        Sort.by(
                                Sort.Direction.ASC,
                                "assetTag"
                        )
                )
        );

        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());

        assertEquals(
                "LAP-INTEGRATION-002",
                page.getContent()
                        .getFirst()
                        .getAssetTag()
        );
    }
}
