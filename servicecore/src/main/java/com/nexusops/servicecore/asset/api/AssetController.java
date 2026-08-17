package com.nexusops.servicecore.asset.api;

import com.nexusops.servicecore.asset.application.AssetSearchCriteria;
import com.nexusops.servicecore.asset.application.AssetService;
import com.nexusops.servicecore.asset.domain.Asset;
import com.nexusops.servicecore.asset.domain.AssetAssignment;
import com.nexusops.servicecore.asset.domain.AssetStatus;
import com.nexusops.servicecore.asset.domain.AssetType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "assetTag",
                    "type",
                    "manufacturer",
                    "model",
                    "serialNumber",
                    "status",
                    "createdAt",
                    "updatedAt"
            );

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(
            @Valid @RequestBody CreateAssetRequest request
    ) {
        Asset asset = assetService.create(
                request.assetTag(),
                request.type(),
                request.manufacturer(),
                request.model(),
                request.serialNumber()
        );

        URI location = URI.create(
                "/api/v1/assets/" + asset.getId()
        );

        return ResponseEntity
                .created(location)
                .body(AssetResponse.from(asset));
    }

    @GetMapping
    public AssetPageResponse search(
            @RequestParam(required = false)
            AssetType type,

            @RequestParam(required = false)
            AssetStatus status,

            @RequestParam(required = false)
            String assigneeId,

            @RequestParam(required = false)
            String manufacturer,

            @RequestParam(name = "q", required = false)
            String query,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(defaultValue = "assetTag,asc")
            String sort
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100"
            );
        }

        Sort parsedSort = parseSort(sort);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                parsedSort
        );

        AssetSearchCriteria criteria =
                new AssetSearchCriteria(
                        type,
                        status,
                        assigneeId,
                        manufacturer,
                        query
                );

        Page<Asset> assets = assetService.search(
                criteria,
                pageable
        );

        return AssetPageResponse.from(assets);
    }

    @GetMapping("/{assetId}")
    public AssetResponse getById(
            @PathVariable UUID assetId
    ) {
        return AssetResponse.from(
                assetService.getById(assetId)
        );
    }

    @PostMapping("/{assetId}/assign")
    public AssetResponse assign(
            @PathVariable UUID assetId,
            @Valid @RequestBody AssignAssetRequest request
    ) {
        return AssetResponse.from(
                assetService.assign(
                        assetId,
                        request.assigneeId()
                )
        );
    }

    @PostMapping("/{assetId}/return")
    public AssetResponse returnAsset(
            @PathVariable UUID assetId
    ) {
        return AssetResponse.from(
                assetService.returnAsset(assetId)
        );
    }

    @PostMapping("/{assetId}/start-maintenance")
    public AssetResponse startMaintenance(
            @PathVariable UUID assetId
    ) {
        return AssetResponse.from(
                assetService.sendToMaintenance(assetId)
        );
    }

    @PostMapping("/{assetId}/complete-maintenance")
    public AssetResponse completeMaintenance(
            @PathVariable UUID assetId
    ) {
        return AssetResponse.from(
                assetService.returnFromMaintenance(assetId)
        );
    }

    @PostMapping("/{assetId}/retire")
    public AssetResponse retire(
            @PathVariable UUID assetId
    ) {
        return AssetResponse.from(
                assetService.retire(assetId)
        );
    }

    @GetMapping("/{assetId}/assignments")
    public List<AssetAssignmentResponse> assignmentHistory(
            @PathVariable UUID assetId
    ) {
        List<AssetAssignment> assignments =
                assetService.getAssignmentHistory(assetId);

        return assignments
                .stream()
                .map(
                        assignment ->
                                AssetAssignmentResponse.from(
                                        assignment,
                                        assetId
                                )
                )
                .toList();
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "sort must use field,direction format"
            );
        }

        String field = parts[0].trim();
        String direction = parts[1].trim();

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException(
                    "unsupported sort field: " + field
            );
        }

        Sort.Direction sortDirection =
                Sort.Direction.fromString(direction);

        return Sort.by(
                sortDirection,
                field
        );
    }
}