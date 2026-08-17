package com.nexusops.servicecore.knowledge.repository;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class KnowledgeArticleRepositoryTests {

    @Autowired
    private KnowledgeArticleRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsKnowledgeArticle() {
        KnowledgeArticle article =
                KnowledgeArticle.create(
                        "VPN guide",
                        "NETWORK",
                        "Restart the VPN client.",
                        "user-123",
                        LocalDate.of(2026, 12, 1)
                );

        repository.saveAndFlush(article);

        assertNotNull(article.getCreatedAt());
        assertNotNull(article.getUpdatedAt());

        entityManager.clear();

        KnowledgeArticle loaded =
                repository.findById(
                        article.getId()
                ).orElseThrow();

        assertEquals(
                "VPN guide",
                loaded.getTitle()
        );

        assertEquals(
                KnowledgeArticleStatus.DRAFT,
                loaded.getStatus()
        );

        assertEquals(
                1,
                loaded.getVersion()
        );
    }
}