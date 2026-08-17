package com.nexusops.servicecore.incident.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIncidentCommentRequest(

        @NotBlank
        @Size(max = 255)
        String authorId,

        @NotBlank
        String content

) {
}
