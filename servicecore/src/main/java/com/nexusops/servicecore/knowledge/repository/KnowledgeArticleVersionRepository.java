package com.nexusops.servicecore.knowledge.repository;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeArticleVersionRepository
        extends JpaRepository<KnowledgeArticleVersion, UUID> {

    List<KnowledgeArticleVersion>
            findByArticleIdOrderByVersionNumberDesc(
                    UUID articleId
            );
}
