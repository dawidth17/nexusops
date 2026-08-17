package com.nexusops.servicecore.asset.application;

import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetAssignment;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;
import com.nexusops.servicecore.asset.repository.AssetAssignmentRepository;
import com.nexusops.servicecore.asset.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTests {

    private static final Instant NOW =
            Instant.parse("2026-08-17T12:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetAssignmentRepository assignmentRepository;

    @Test
    void createsAsset() {
        AssetService service = createService();

        when(assetRepository.existsByAssetTag(
                "LAP-000123"
        )).thenReturn(false);

        when(assetRepository.existsBySerialNumber(
                "SN-123456"
        )).thenReturn(false);

        when(assetRepository.save(any(Asset.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        Asset asset = service.create(
                "LAP-000123",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 7450",
                "SN-123456"
        );

        assertEquals(
                AssetStatus.AVAILABLE,
                asset.getStatus()
        );

        verify(assetRepository).save(asset);
    }

    @Test
    void rejectsDuplicateAssetTag() {
        AssetService service = createService();

        when(assetRepository.existsByAssetTag(
                "LAP-000123"
        )).thenReturn(true);

        assertThrows(
                AssetIdentifierConflictException.class,
                () -> service.create(
                        "LAP-000123",
                        AssetType.LAPTOP,
                        "Dell",
                        "Latitude 7450",
                        "SN-123456"
                )
        );
    }

    @Test
    void assignsAssetAndCreatesHistory() {
        AssetService service = createService();

        UUID assetId = UUID.randomUUID();
        Asset asset = createAsset();

        when(assetRepository.findById(assetId))
                .thenReturn(Optional.of(asset));

        service.assign(
                assetId,
                "user-123"
        );

        assertEquals(
                AssetStatus.ASSIGNED,
                asset.getStatus()
        );

        assertEquals(
                "user-123",
                asset.getAssigneeId()
        );

        verify(assignmentRepository).save(
                any(AssetAssignment.class)
        );
    }

    @Test
    void returnsAssetAndClosesHistory() {
        AssetService service = createService();

        UUID assetId = UUID.randomUUID();
        Asset asset = createAsset();

        asset.assignTo("user-123");

        AssetAssignment assignment =
                AssetAssignment.create(
                        asset,
                        "user-123",
                        NOW.minusSeconds(3600)
                );

        when(assetRepository.findById(assetId))
                .thenReturn(Optional.of(asset));

        when(
                assignmentRepository
                        .findByAsset_IdAndReturnedAtIsNull(
                                assetId
                        )
        ).thenReturn(Optional.of(assignment));

        service.returnAsset(assetId);

        assertEquals(
                AssetStatus.AVAILABLE,
                asset.getStatus()
        );

        assertNull(asset.getAssigneeId());

        assertEquals(
                NOW,
                assignment.getReturnedAt()
        );
    }

    @Test
    void rejectsMissingAsset() {
        AssetService service = createService();

        UUID assetId = UUID.randomUUID();

        when(assetRepository.findById(assetId))
                .thenReturn(Optional.empty());

        assertThrows(
                AssetNotFoundException.class,
                () -> service.getById(assetId)
        );
    }

    private AssetService createService() {
        return new AssetService(
                assetRepository,
                assignmentRepository,
                CLOCK
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
