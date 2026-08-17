package com.nexusops.servicecore.knowledge.api;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import org.springframework.data.domain.Page;

import java.util.List;

public record KnowledgeArticlePageResponse(
        List<KnowledgeArticleResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static KnowledgeArticlePageResponse from(
            Page<KnowledgeArticle> articles
    ) {
        return new KnowledgeArticlePageResponse(
                articles.getContent()
                        .stream()
                        .map(KnowledgeArticleResponse::from)
                        .toList(),
                articles.getNumber(),
                articles.getSize(),
                articles.getTotalElements(),
                articles.getTotalPages()
        );
    }
}
