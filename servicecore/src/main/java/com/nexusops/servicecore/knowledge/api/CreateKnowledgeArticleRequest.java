package com.nexusops.servicecore.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateKnowledgeArticleRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 100)
        String category,

        @NotBlank
        String steps,

        @NotBlank
        @Size(max = 255)
        String ownerId,

        @NotNull
        LocalDate reviewDate

) {
}
