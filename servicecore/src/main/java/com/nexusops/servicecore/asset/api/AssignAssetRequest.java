package com.nexusops.servicecore.asset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignAssetRequest(

        @NotBlank
        @Size(max = 255)
        String assigneeId

) {
}
