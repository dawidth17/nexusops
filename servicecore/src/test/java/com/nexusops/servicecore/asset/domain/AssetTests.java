package com.nexusops.servicecore.asset.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetTests {

    @Test
    void createsAvailableAsset() {
        Asset asset = createAsset();

        assertEquals(
                "LAP-000123",
                asset.getAssetTag()
        );

        assertEquals(
                AssetType.LAPTOP,
                asset.getType()
        );

        assertEquals(
                "Dell",
                asset.getManufacturer()
        );

        assertEquals(
                "Latitude 7450",
                asset.getModel()
        );

        assertEquals(
                "SN-123456",
                asset.getSerialNumber()
        );

        assertEquals(
                AssetStatus.AVAILABLE,
                asset.getStatus()
        );

        assertNull(asset.getAssigneeId());
    }

    @Test
    void trimsAssetFields() {
        Asset asset = Asset.create(
                "  LAP-000123  ",
                AssetType.LAPTOP,
                "  Dell  ",
                "  Latitude 7450  ",
                "  SN-123456  "
        );

        assertEquals(
                "LAP-000123",
                asset.getAssetTag()
        );
        assertEquals(
                "Dell",
                asset.getManufacturer()
        );
        assertEquals(
                "Latitude 7450",
                asset.getModel()
        );
        assertEquals(
                "SN-123456",
                asset.getSerialNumber()
        );
    }

    @Test
    void assignsAvailableAsset() {
        Asset asset = createAsset();

        asset.assignTo("user-123");

        assertEquals(
                AssetStatus.ASSIGNED,
                asset.getStatus()
        );

        assertEquals(
                "user-123",
                asset.getAssigneeId()
        );
    }

    @Test
    void returnsAssignedAsset() {
        Asset asset = createAsset();

        asset.assignTo("user-123");
        asset.returnAsset();

        assertEquals(
                AssetStatus.AVAILABLE,
                asset.getStatus()
        );

        assertNull(asset.getAssigneeId());
    }

    @Test
    void rejectsAssigningNonAvailableAsset() {
        Asset asset = createAsset();

        asset.assignTo("user-123");

        assertThrows(
                InvalidAssetOperationException.class,
                () -> asset.assignTo("user-456")
        );
    }

    @Test
    void rejectsReturningAvailableAsset() {
        Asset asset = createAsset();

        assertThrows(
                InvalidAssetOperationException.class,
                asset::returnAsset
        );
    }

    @Test
    void sendsAvailableAssetToMaintenance() {
        Asset asset = createAsset();

        asset.sendToMaintenance();

        assertEquals(
                AssetStatus.MAINTENANCE,
                asset.getStatus()
        );
    }

    @Test
    void returnsAssetFromMaintenance() {
        Asset asset = createAsset();

        asset.sendToMaintenance();
        asset.returnFromMaintenance();

        assertEquals(
                AssetStatus.AVAILABLE,
                asset.getStatus()
        );
    }

    @Test
    void rejectsMaintenanceForAssignedAsset() {
        Asset asset = createAsset();

        asset.assignTo("user-123");

        assertThrows(
                InvalidAssetOperationException.class,
                asset::sendToMaintenance
        );
    }

    @Test
    void retiresAvailableAsset() {
        Asset asset = createAsset();

        asset.retire();

        assertEquals(
                AssetStatus.RETIRED,
                asset.getStatus()
        );

        assertNull(asset.getAssigneeId());
    }

    @Test
    void rejectsRetiringAssignedAsset() {
        Asset asset = createAsset();

        asset.assignTo("user-123");

        assertThrows(
                InvalidAssetOperationException.class,
                asset::retire
        );
    }

    @Test
    void rejectsBlankAssetTag() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Asset.create(
                        " ",
                        AssetType.LAPTOP,
                        "Dell",
                        "Latitude 7450",
                        "SN-123456"
                )
        );
    }

    @Test
    void rejectsMissingAssetType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Asset.create(
                        "LAP-000123",
                        null,
                        "Dell",
                        "Latitude 7450",
                        "SN-123456"
                )
        );
    }

    @Test
    void rejectsBlankAssignee() {
        Asset asset = createAsset();

        assertThrows(
                IllegalArgumentException.class,
                () -> asset.assignTo(" ")
        );
    }

    private Asset createAsset() {
        return Asset.create(
                "LAP-000123",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 7450",
                "SN-123456"
        );
    }
}
