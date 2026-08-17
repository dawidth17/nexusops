package com.nexusops.servicecore.knowledge.api;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record KnowledgeArticleResponse(
        UUID id,
        String title,
        String category,
        String steps,
        KnowledgeArticleStatus status,
        String ownerId,
        LocalDate reviewDate,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public static KnowledgeArticleResponse from(
            KnowledgeArticle article
    ) {
        return new KnowledgeArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getCategory(),
                article.getSteps(),
                article.getStatus(),
                article.getOwnerId(),
                article.getReviewDate(),
                article.getVersion(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
