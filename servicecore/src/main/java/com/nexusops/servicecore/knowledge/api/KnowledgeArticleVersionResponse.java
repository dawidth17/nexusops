package com.nexusops.servicecore.knowledge.api;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record KnowledgeArticleVersionResponse(
        UUID id,
        UUID articleId,
        int versionNumber,
        String title,
        String category,
        String steps,
        String ownerId,
        LocalDate reviewDate,
        Instant createdAt
) {

    public static KnowledgeArticleVersionResponse from(
            KnowledgeArticleVersion version
    ) {
        return new KnowledgeArticleVersionResponse(
                version.getId(),
                version.getArticleId(),
                version.getVersionNumber(),
                version.getTitle(),
                version.getCategory(),
                version.getSteps(),
                version.getOwnerId(),
                version.getReviewDate(),
                version.getCreatedAt()
        );
    }
}