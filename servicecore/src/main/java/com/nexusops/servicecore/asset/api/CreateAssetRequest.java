package com.nexusops.servicecore.asset.api;

import com.nexusops.servicecore.asset.domain.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAssetRequest(

        @NotBlank
        @Size(max = 100)
        String assetTag,

        @NotNull
        AssetType type,

        @NotBlank
        @Size(max = 100)
        String manufacturer,

        @NotBlank
        @Size(max = 150)
        String model,

        @NotBlank
        @Size(max = 150)
        String serialNumber

) {
}
