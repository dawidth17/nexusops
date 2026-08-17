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

    private static final String UNIQUE_SEARCH_TERM =
            "spec-knowledge-only-8f3a";

    @Autowired
    private KnowledgeArticleRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void createArticles() {
        KnowledgeArticle networkArticle =
                KnowledgeArticle.create(
                        "Network guide "
                                + UNIQUE_SEARCH_TERM,
                        "SPEC_NETWORK",
                        "Unique troubleshooting procedure.",
                        "spec-user-123",
                        LocalDate.of(
                                2026,
                                9,
                                1
                        )
                );

        networkArticle.publish();

        KnowledgeArticle hardwareArticle =
                KnowledgeArticle.create(
                        "Hardware startup guide",
                        "SPEC_HARDWARE",
                        "Check the charger and power state.",
                        "spec-user-456",
                        LocalDate.of(
                                2027,
                                1,
                                1
                        )
                );

        repository.saveAll(
                List.of(
                        networkArticle,
                        hardwareArticle
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
                                                "spec_network"
                                        )
                        );

        List<KnowledgeArticle> articles =
                repository.findAll(specification);

        assertEquals(
                1,
                articles.size()
        );

        assertEquals(
                "Network guide "
                        + UNIQUE_SEARCH_TERM,
                articles.getFirst()
                        .getTitle()
        );
    }

    @Test
    void searchesArticleText() {
        List<KnowledgeArticle> articles =
                repository.findAll(
                        KnowledgeArticleSpecifications
                                .containsText(
                                        UNIQUE_SEARCH_TERM
                                )
                );

        assertEquals(
                1,
                articles.size()
        );

        assertEquals(
                "Network guide "
                        + UNIQUE_SEARCH_TERM,
                articles.getFirst()
                        .getTitle()
        );
    }

    @Test
    void findsArticlesDueForReview() {
        Specification<KnowledgeArticle> specification =
                Specification
                        .<KnowledgeArticle>unrestricted()
                        .and(
                                KnowledgeArticleSpecifications
                                        .hasCategory(
                                                "SPEC_NETWORK"
                                        )
                        )
                        .and(
                                KnowledgeArticleSpecifications
                                        .reviewDueBy(
                                                LocalDate.of(
                                                        2026,
                                                        10,
                                                        1
                                                )
                                        )
                        );

        List<KnowledgeArticle> articles =
                repository.findAll(specification);

        assertEquals(
                1,
                articles.size()
        );
    }
}