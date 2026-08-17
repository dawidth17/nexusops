package com.nexusops.servicecore.asset.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetAssignmentTests {

    @Test
    void createsActiveAssignment() {
        Asset asset = createAsset();

        Instant assignedAt =
                Instant.parse("2026-08-17T10:00:00Z");

        AssetAssignment assignment =
                AssetAssignment.create(
                        asset,
                        "user-123",
                        assignedAt
                );

        assertEquals(
                asset,
                assignment.getAsset()
        );

        assertEquals(
                "user-123",
                assignment.getAssigneeId()
        );

        assertEquals(
                assignedAt,
                assignment.getAssignedAt()
        );

        assertNull(assignment.getReturnedAt());
        assertTrue(assignment.isActive());
    }

    @Test
    void closesAssignment() {
        AssetAssignment assignment =
                createAssignment();

        Instant returnedAt =
                Instant.parse("2026-08-18T10:00:00Z");

        assignment.markReturned(returnedAt);

        assertEquals(
                returnedAt,
                assignment.getReturnedAt()
        );
    }

    @Test
    void rejectsReturnBeforeAssignment() {
        AssetAssignment assignment =
                createAssignment();

        assertThrows(
                IllegalArgumentException.class,
                () -> assignment.markReturned(
                        Instant.parse(
                                "2026-08-16T10:00:00Z"
                        )
                )
        );
    }

    @Test
    void rejectsClosingAssignmentTwice() {
        AssetAssignment assignment =
                createAssignment();

        assignment.markReturned(
                Instant.parse("2026-08-18T10:00:00Z")
        );

        assertThrows(
                InvalidAssetOperationException.class,
                () -> assignment.markReturned(
                        Instant.parse(
                                "2026-08-19T10:00:00Z"
                        )
                )
        );
    }

    @Test
    void rejectsBlankAssignee() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AssetAssignment.create(
                        createAsset(),
                        " ",
                        Instant.parse(
                                "2026-08-17T10:00:00Z"
                        )
                )
        );
    }

    private AssetAssignment createAssignment() {
        return AssetAssignment.create(
                createAsset(),
                "user-123",
                Instant.parse("2026-08-17T10:00:00Z")
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
