package com.nexusops.servicecore.asset.repository;

import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AssetSpecificationsTests {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void createAssets() {
        Asset dellLaptop = Asset.create(
                "LAP-SEARCH-001",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 7450",
                "SEARCH-SERIAL-001"
        );

        dellLaptop.assignTo("user-123");

        Asset lenovoLaptop = Asset.create(
                "LAP-SEARCH-002",
                AssetType.LAPTOP,
                "Lenovo",
                "ThinkPad T14",
                "SEARCH-SERIAL-002"
        );

        Asset server = Asset.create(
                "SRV-SEARCH-001",
                AssetType.SERVER,
                "Dell",
                "PowerEdge R760",
                "SEARCH-SERIAL-003"
        );

        server.sendToMaintenance();

        assetRepository.saveAll(
                List.of(
                        dellLaptop,
                        lenovoLaptop,
                        server
                )
        );

        assetRepository.flush();
        entityManager.clear();
    }

    @Test
    void filtersByType() {
        Specification<Asset> specification =
                AssetSpecifications.hasType(
                        AssetType.LAPTOP
                );

        List<Asset> assets =
                assetRepository.findAll(specification);

        assertEquals(2, assets.size());
    }

    @Test
    void filtersByStatus() {
        Specification<Asset> specification =
                AssetSpecifications.hasStatus(
                        AssetStatus.MAINTENANCE
                );

        List<Asset> assets =
                assetRepository.findAll(specification);

        assertEquals(1, assets.size());

        assertEquals(
                "SRV-SEARCH-001",
                assets.getFirst().getAssetTag()
        );
    }

    @Test
    void filtersByAssignee() {
        Specification<Asset> specification =
                AssetSpecifications.hasAssigneeId(
                        "user-123"
                );

        List<Asset> assets =
                assetRepository.findAll(specification);

        assertEquals(1, assets.size());

        assertEquals(
                "LAP-SEARCH-001",
                assets.getFirst().getAssetTag()
        );
    }

    @Test
    void filtersByManufacturerCaseInsensitive() {
        Specification<Asset> specification =
                AssetSpecifications.hasManufacturer(
                        "dell"
                );

        List<Asset> assets =
                assetRepository.findAll(specification);

        assertEquals(2, assets.size());
    }

    @Test
    void searchesAcrossAssetFields() {
        Specification<Asset> specification =
                AssetSpecifications.containsText(
                        "thinkpad"
                );

        List<Asset> assets =
                assetRepository.findAll(specification);

        assertEquals(1, assets.size());

        assertEquals(
                "LAP-SEARCH-002",
                assets.getFirst().getAssetTag()
        );
    }

    @Test
    void combinesFiltersWithPagination() {
        Specification<Asset> specification =
                Specification
                        .<Asset>unrestricted()
                        .and(
                                AssetSpecifications.hasType(
                                        AssetType.LAPTOP
                                )
                        )
                        .and(
                                AssetSpecifications.containsText(
                                        "search"
                                )
                        );

        var page = assetRepository.findAll(
                specification,
                PageRequest.of(0, 1)
        );

        assertEquals(
                1,
                page.getContent().size()
        );

        assertEquals(
                2,
                page.getTotalElements()
        );

        assertEquals(
                2,
                page.getTotalPages()
        );
    }
}
