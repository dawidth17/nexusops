package com.nexusops.servicecore.asset.api;

import com.nexusops.servicecore.asset.application.AssetIdentifierConflictException;
import com.nexusops.servicecore.asset.application.AssetNotFoundException;
import com.nexusops.servicecore.asset.application.AssetService;
import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetAssignment;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;
import com.nexusops.servicecore.asset.domain.InvalidAssetOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssetController.class)
class AssetControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetService assetService;

    @Test
    void createsAsset() throws Exception {
        UUID assetId = UUID.randomUUID();

        Asset asset = mockAsset(
                assetId,
                AssetStatus.AVAILABLE,
                null
        );

        when(assetService.create(
                "LAP-000123",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 7450",
                "SN-123456"
        )).thenReturn(asset);

        mockMvc.perform(
                        post("/api/v1/assets")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "assetTag": "LAP-000123",
                                          "type": "LAPTOP",
                                          "manufacturer": "Dell",
                                          "model": "Latitude 7450",
                                          "serialNumber": "SN-123456"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                "/api/v1/assets/" + assetId
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(assetId.toString())
                )
                .andExpect(
                        jsonPath("$.assetTag")
                                .value("LAP-000123")
                )
                .andExpect(
                        jsonPath("$.type")
                                .value("LAPTOP")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE")
                );
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/assets")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "assetTag": "",
                                          "type": "LAPTOP",
                                          "manufacturer": "Dell",
                                          "model": "Latitude 7450",
                                          "serialNumber": "SN-123456"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.title")
                                .value("Invalid request")
                )
                .andExpect(
                        jsonPath("$.errors.assetTag")
                                .exists()
                );

        verifyNoInteractions(assetService);
    }

    @Test
    void getsAssetById() throws Exception {
        UUID assetId = UUID.randomUUID();

        Asset asset = mockAsset(
                assetId,
                AssetStatus.AVAILABLE,
                null
        );

        when(assetService.getById(assetId))
                .thenReturn(asset);

        mockMvc.perform(
                        get(
                                "/api/v1/assets/{assetId}",
                                assetId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(assetId.toString())
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE")
                );
    }

    @Test
    void assignsAsset() throws Exception {
        UUID assetId = UUID.randomUUID();

        Asset asset = mockAsset(
                assetId,
                AssetStatus.ASSIGNED,
                "user-123"
        );

        when(assetService.assign(
                assetId,
                "user-123"
        )).thenReturn(asset);

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/assign",
                                assetId
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "assigneeId": "user-123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("ASSIGNED")
                )
                .andExpect(
                        jsonPath("$.assigneeId")
                                .value("user-123")
                );
    }

    @Test
    void returnsAsset() throws Exception {
        UUID assetId = UUID.randomUUID();

        Asset asset = mockAsset(
                assetId,
                AssetStatus.AVAILABLE,
                null
        );

        when(assetService.returnAsset(assetId))
                .thenReturn(asset);

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/return",
                                assetId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE")
                )
                .andExpect(
                        jsonPath("$.assigneeId")
                                .doesNotExist()
                );
    }

    @Test
    void startsAndCompletesMaintenance() throws Exception {
        UUID assetId = UUID.randomUUID();

        Asset maintenanceAsset = mockAsset(
                assetId,
                AssetStatus.MAINTENANCE,
                null
        );

        Asset availableAsset = mockAsset(
                assetId,
                AssetStatus.AVAILABLE,
                null
        );

        when(assetService.sendToMaintenance(assetId))
                .thenReturn(maintenanceAsset);

        when(assetService.returnFromMaintenance(assetId))
                .thenReturn(availableAsset);

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/start-maintenance",
                                assetId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("MAINTENANCE")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/complete-maintenance",
                                assetId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE")
                );
    }

    @Test
    void retiresAsset() throws Exception {
        UUID assetId = UUID.randomUUID();

        Asset asset = mockAsset(
                assetId,
                AssetStatus.RETIRED,
                null
        );

        when(assetService.retire(assetId))
                .thenReturn(asset);

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/retire",
                                assetId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("RETIRED")
                );
    }

    @Test
    void listsAssignmentHistory() throws Exception {
        UUID assetId = UUID.randomUUID();

        AssetAssignment assignment =
                mock(AssetAssignment.class);

        UUID assignmentId = UUID.randomUUID();

        when(assignment.getId())
                .thenReturn(assignmentId);

        when(assignment.getAssigneeId())
                .thenReturn("user-123");

        when(assignment.getAssignedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-17T10:00:00Z"
                        )
                );

        when(assignment.getReturnedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-18T10:00:00Z"
                        )
                );

        when(assignment.isActive())
                .thenReturn(false);

        when(assetService.getAssignmentHistory(assetId))
                .thenReturn(List.of(assignment));

        mockMvc.perform(
                        get(
                                "/api/v1/assets/{assetId}/assignments",
                                assetId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(
                                        assignmentId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$[0].assetId")
                                .value(assetId.toString())
                )
                .andExpect(
                        jsonPath("$[0].assigneeId")
                                .value("user-123")
                )
                .andExpect(
                        jsonPath("$[0].active")
                                .value(false)
                );
    }

    @Test
    void returnsNotFoundForMissingAsset() throws Exception {
        UUID assetId = UUID.randomUUID();

        when(assetService.getById(assetId))
                .thenThrow(
                        new AssetNotFoundException(
                                assetId
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/assets/{assetId}",
                                assetId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value("Asset not found")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:nexusops:problem:asset-not-found"
                                )
                );
    }

    @Test
    void returnsConflictForDuplicateIdentifier()
            throws Exception {

        when(assetService.create(
                "LAP-000123",
                AssetType.LAPTOP,
                "Dell",
                "Latitude 7450",
                "SN-123456"
        )).thenThrow(
                new AssetIdentifierConflictException(
                        "asset tag",
                        "LAP-000123"
                )
        );

        mockMvc.perform(
                        post("/api/v1/assets")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "assetTag": "LAP-000123",
                                          "type": "LAPTOP",
                                          "manufacturer": "Dell",
                                          "model": "Latitude 7450",
                                          "serialNumber": "SN-123456"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Asset identifier conflict"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                );
    }

    @Test
    void returnsConflictForInvalidLifecycleOperation()
            throws Exception {

        UUID assetId = UUID.randomUUID();

        when(assetService.retire(assetId))
                .thenThrow(
                        new InvalidAssetOperationException(
                                "assigned assets must be returned before retirement"
                        )
                );

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/retire",
                                assetId
                        )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Invalid asset operation"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                );
    }

    private Asset mockAsset(
            UUID assetId,
            AssetStatus status,
            String assigneeId
    ) {
        Asset asset = mock(Asset.class);

        when(asset.getId())
                .thenReturn(assetId);

        when(asset.getAssetTag())
                .thenReturn("LAP-000123");

        when(asset.getType())
                .thenReturn(AssetType.LAPTOP);

        when(asset.getManufacturer())
                .thenReturn("Dell");

        when(asset.getModel())
                .thenReturn("Latitude 7450");

        when(asset.getSerialNumber())
                .thenReturn("SN-123456");

        when(asset.getStatus())
                .thenReturn(status);

        when(asset.getAssigneeId())
                .thenReturn(assigneeId);

        when(asset.getCreatedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-17T10:00:00Z"
                        )
                );

        when(asset.getUpdatedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-17T10:00:00Z"
                        )
                );

        return asset;
    }
}
