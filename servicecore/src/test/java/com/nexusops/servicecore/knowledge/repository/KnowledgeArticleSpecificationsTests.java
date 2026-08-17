package com.nexusops.servicecore.knowledge.repository;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class KnowledgeArticleSpecificationsTests {

    @Autowired
    private KnowledgeArticleRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void createArticles() {
        KnowledgeArticle vpn =
                KnowledgeArticle.create(
                        "GlobalProtect VPN troubleshooting",
                        "NETWORK",
                        "Restart GlobalProtect.",
                        "user-123",
                        LocalDate.of(2026, 9, 1)
                );

        vpn.publish();

        KnowledgeArticle laptop =
                KnowledgeArticle.create(
                        "Laptop startup troubleshooting",
                        "HARDWARE",
                        "Check the charger and power state.",
                        "user-456",
                        LocalDate.of(2027, 1, 1)
                );

        repository.saveAll(
                List.of(
                        vpn,
                        laptop
                )
        );

        repository.flush();
        entityManager.clear();
    }

    @Test
    void filtersByStatusAndCategory() {
        Specification<KnowledgeArticle> specification =
                Specification
                        .<KnowledgeArticle>unrestricted()
                        .and(
                                KnowledgeArticleSpecifications
                                        .hasStatus(
                                                KnowledgeArticleStatus.PUBLISHED
                                        )
                        )
                        .and(
                                KnowledgeArticleSpecifications
                                        .hasCategory(
                                                "network"
                                        )
                        );

        List<KnowledgeArticle> articles =
                repository.findAll(specification);

        assertEquals(1, articles.size());

        assertEquals(
                "GlobalProtect VPN troubleshooting",
                articles.getFirst().getTitle()
        );
    }

    @Test
    void searchesArticleText() {
        List<KnowledgeArticle> articles =
                repository.findAll(
                        KnowledgeArticleSpecifications
                                .containsText(
                                        "globalprotect"
                                )
                );

        assertEquals(1, articles.size());
    }

    @Test
    void findsArticlesDueForReview() {
        List<KnowledgeArticle> articles =
                repository.findAll(
                        KnowledgeArticleSpecifications
                                .reviewDueBy(
                                        LocalDate.of(
                                                2026,
                                                10,
                                                1
                                        )
                                )
                );

        assertEquals(1, articles.size());
    }
}