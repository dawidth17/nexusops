package com.nexusops.servicecore.knowledge.repository;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface KnowledgeArticleRepository
        extends JpaRepository<KnowledgeArticle, UUID>,
        JpaSpecificationExecutor<KnowledgeArticle> {
}