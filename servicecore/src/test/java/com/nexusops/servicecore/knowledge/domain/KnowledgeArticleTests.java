package com.nexusops.servicecore.knowledge.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeArticleTests {

    @Test
    void createsDraftArticleAtVersionOne() {
        KnowledgeArticle article = createArticle();

        assertNull(article.getId());

        assertEquals(
                KnowledgeArticleStatus.DRAFT,
                article.getStatus()
        );

        assertEquals(
                1,
                article.getVersion()
        );
    }

    @Test
    void updatesArticleAndIncrementsVersion() {
        KnowledgeArticle article = createArticle();

        article.update(
                "Updated VPN guide",
                "NETWORK",
                "Updated steps",
                "user-456",
                LocalDate.of(2027, 1, 1)
        );

        assertEquals(
                "Updated VPN guide",
                article.getTitle()
        );

        assertEquals(
                2,
                article.getVersion()
        );
    }

    @Test
    void publishesDraftArticle() {
        KnowledgeArticle article = createArticle();

        article.publish();

        assertEquals(
                KnowledgeArticleStatus.PUBLISHED,
                article.getStatus()
        );
    }

    @Test
    void rejectsPublishingTwice() {
        KnowledgeArticle article = createArticle();

        article.publish();

        assertThrows(
                InvalidKnowledgeArticleOperationException.class,
                article::publish
        );
    }

    @Test
    void rejectsEditingArchivedArticle() {
        KnowledgeArticle article = createArticle();

        article.archive();

        assertThrows(
                InvalidKnowledgeArticleOperationException.class,
                () -> article.update(
                        "Updated",
                        "NETWORK",
                        "Updated",
                        "user-123",
                        LocalDate.of(2027, 1, 1)
                )
        );
    }

    @Test
    void archiveIsIdempotent() {
        KnowledgeArticle article = createArticle();

        article.archive();
        article.archive();

        assertEquals(
                KnowledgeArticleStatus.ARCHIVED,
                article.getStatus()
        );
    }

    private KnowledgeArticle createArticle() {
        return KnowledgeArticle.create(
                "Reset GlobalProtect VPN",
                "NETWORK",
                "Restart GlobalProtect and reconnect.",
                "user-123",
                LocalDate.of(2026, 12, 1)
        );
    }
}
