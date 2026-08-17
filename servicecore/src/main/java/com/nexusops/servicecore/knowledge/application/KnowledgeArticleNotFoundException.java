package com.nexusops.servicecore.knowledge.application;

import java.util.UUID;

public class KnowledgeArticleNotFoundException
        extends RuntimeException {

    public KnowledgeArticleNotFoundException(
            UUID articleId
    ) {
        super(
                "knowledge article not found: "
                        + articleId
        );
    }
}
