package com.nexusops.servicecore.knowledge.application;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;

import java.time.LocalDate;

public record KnowledgeArticleSearchCriteria(
        KnowledgeArticleStatus status,
        String category,
        String ownerId,
        LocalDate reviewBefore,
        String query
) {
}
