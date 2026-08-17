package com.nexusops.servicecore.knowledge.domain;

public class InvalidKnowledgeArticleOperationException
        extends RuntimeException {

    public InvalidKnowledgeArticleOperationException(
            String message
    ) {
        super(message);
    }
}
