package com.nexusops.servicecore.knowledge;

import com.nexusops.servicecore.audit.application.AuditService;
import com.nexusops.servicecore.audit.domain.AuditAction;
import com.nexusops.servicecore.audit.domain.AuditEntityType;
import com.nexusops.servicecore.audit.domain.AuditEntry;
import com.nexusops.servicecore.knowledge.application.KnowledgeArticleService;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleVersion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class KnowledgeAuditIntegrationTests {

    @Autowired
    private KnowledgeArticleService articleService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void versionsArticleAndCreatesAuditHistory() {
        KnowledgeArticle article =
                articleService.create(
                        "VPN troubleshooting",
                        "NETWORK",
                        "Restart GlobalProtect.",
                        "user-123",
                        LocalDate.of(
                                2026,
                                12,
                                1
                        )
                );

        entityManager.flush();

        assertNotNull(article.getId());

        assertEquals(
                1,
                article.getVersion()
        );

        articleService.update(
                article.getId(),
                "GlobalProtect troubleshooting",
                "NETWORK",
                "Restart GlobalProtect and reconnect.",
                "user-123",
                LocalDate.of(
                        2027,
                        1,
                        1
                )
        );

        articleService.publish(
                article.getId()
        );

        entityManager.flush();
        entityManager.clear();

        KnowledgeArticle published =
                articleService.getById(
                        article.getId()
                );

        assertEquals(
                KnowledgeArticleStatus.PUBLISHED,
                published.getStatus()
        );

        assertEquals(
                2,
                published.getVersion()
        );

        List<KnowledgeArticleVersion> versions =
                articleService.getVersions(
                        article.getId()
                );

        assertEquals(
                2,
                versions.size()
        );

        assertEquals(
                2,
                versions.getFirst()
                        .getVersionNumber()
        );

        assertEquals(
                1,
                versions.getLast()
                        .getVersionNumber()
        );

        Page<AuditEntry> audit =
                auditService.getHistory(
                        AuditEntityType.KNOWLEDGE_ARTICLE,
                        article.getId(),
                        PageRequest.of(
                                0,
                                20
                        )
                );

        assertEquals(
                3,
                audit.getTotalElements()
        );

        assertTrue(
                audit.getContent()
                        .stream()
                        .anyMatch(
                                entry ->
                                        entry.getAction()
                                                == AuditAction.KNOWLEDGE_ARTICLE_CREATED
                        )
        );

        assertTrue(
                audit.getContent()
                        .stream()
                        .anyMatch(
                                entry ->
                                        entry.getAction()
                                                == AuditAction.KNOWLEDGE_ARTICLE_UPDATED
                        )
        );

        assertTrue(
                audit.getContent()
                        .stream()
                        .anyMatch(
                                entry ->
                                        entry.getAction()
                                                == AuditAction.KNOWLEDGE_ARTICLE_PUBLISHED
                        )
        );
    }

    @Test
    void archivesPublishedArticleAndAuditsTransition() {
        KnowledgeArticle article =
                articleService.create(
                        "Legacy VPN guide",
                        "NETWORK",
                        "Legacy instructions.",
                        "user-123",
                        LocalDate.of(
                                2026,
                                9,
                                1
                        )
                );

        articleService.publish(
                article.getId()
        );

        articleService.archive(
                article.getId()
        );

        entityManager.flush();
        entityManager.clear();

        KnowledgeArticle archived =
                articleService.getById(
                        article.getId()
                );

        assertEquals(
                KnowledgeArticleStatus.ARCHIVED,
                archived.getStatus()
        );

        Page<AuditEntry> audit =
                auditService.getHistory(
                        AuditEntityType.KNOWLEDGE_ARTICLE,
                        article.getId(),
                        PageRequest.of(
                                0,
                                20
                        )
                );

        AuditEntry archiveEntry =
                audit.getContent()
                        .stream()
                        .filter(
                                entry ->
                                        entry.getAction()
                                                == AuditAction.KNOWLEDGE_ARTICLE_ARCHIVED
                        )
                        .findFirst()
                        .orElseThrow();

        assertNotNull(
                archiveEntry.getBeforeSummary()
        );

        assertNotNull(
                archiveEntry.getAfterSummary()
        );

        assertNull(
                archiveEntry.getCorrelationId()
        );

        assertEquals(
                "dev-user",
                archiveEntry.getActorId()
        );
    }
}
