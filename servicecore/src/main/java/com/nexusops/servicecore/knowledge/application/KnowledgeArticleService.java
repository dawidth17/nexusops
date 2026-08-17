package com.nexusops.servicecore.knowledge.application;

import com.nexusops.servicecore.audit.application.AuditService;
import com.nexusops.servicecore.audit.domain.AuditAction;
import com.nexusops.servicecore.audit.domain.AuditEntityType;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleVersion;
import com.nexusops.servicecore.knowledge.repository.KnowledgeArticleRepository;
import com.nexusops.servicecore.knowledge.repository.KnowledgeArticleSpecifications;
import com.nexusops.servicecore.knowledge.repository.KnowledgeArticleVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class KnowledgeArticleService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeArticleVersionRepository versionRepository;
    private final AuditService auditService;
    private final Clock clock;

    public KnowledgeArticleService(
            KnowledgeArticleRepository articleRepository,
            KnowledgeArticleVersionRepository versionRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.articleRepository = articleRepository;
        this.versionRepository = versionRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    public KnowledgeArticle create(
            String title,
            String category,
            String steps,
            String ownerId,
            LocalDate reviewDate
    ) {
        KnowledgeArticle article =
                KnowledgeArticle.create(
                        title,
                        category,
                        steps,
                        ownerId,
                        reviewDate
                );

        KnowledgeArticle savedArticle =
                articleRepository.save(article);

        versionRepository.save(
                KnowledgeArticleVersion.snapshot(
                        savedArticle,
                        clock.instant()
                )
        );

        auditService.record(
                AuditAction.KNOWLEDGE_ARTICLE_CREATED,
                AuditEntityType.KNOWLEDGE_ARTICLE,
                savedArticle.getId(),
                null,
                summary(savedArticle)
        );

        return savedArticle;
    }

    @Transactional(readOnly = true)
    public KnowledgeArticle getById(
            UUID articleId
    ) {
        return findArticle(articleId);
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeArticle> search(
            KnowledgeArticleSearchCriteria criteria,
            Pageable pageable
    ) {
        Specification<KnowledgeArticle> specification =
                Specification
                        .<KnowledgeArticle>unrestricted()
                        .and(
                                KnowledgeArticleSpecifications
                                        .hasStatus(
                                                criteria.status()
                                        )
                        )
                        .and(
                                KnowledgeArticleSpecifications
                                        .hasCategory(
                                                criteria.category()
                                        )
                        )
                        .and(
                                KnowledgeArticleSpecifications
                                        .hasOwnerId(
                                                criteria.ownerId()
                                        )
                        )
                        .and(
                                KnowledgeArticleSpecifications
                                        .reviewDueBy(
                                                criteria.reviewBefore()
                                        )
                        )
                        .and(
                                KnowledgeArticleSpecifications
                                        .containsText(
                                                criteria.query()
                                        )
                        );

        return articleRepository.findAll(
                specification,
                pageable
        );
    }

    public KnowledgeArticle update(
            UUID articleId,
            String title,
            String category,
            String steps,
            String ownerId,
            LocalDate reviewDate
    ) {
        KnowledgeArticle article =
                findArticle(articleId);

        String before = summary(article);

        article.update(
                title,
                category,
                steps,
                ownerId,
                reviewDate
        );

        versionRepository.save(
                KnowledgeArticleVersion.snapshot(
                        article,
                        clock.instant()
                )
        );

        auditService.record(
                AuditAction.KNOWLEDGE_ARTICLE_UPDATED,
                AuditEntityType.KNOWLEDGE_ARTICLE,
                article.getId(),
                before,
                summary(article)
        );

        return article;
    }

    public KnowledgeArticle publish(
            UUID articleId
    ) {
        KnowledgeArticle article =
                findArticle(articleId);

        String before = summary(article);

        article.publish();

        auditService.record(
                AuditAction.KNOWLEDGE_ARTICLE_PUBLISHED,
                AuditEntityType.KNOWLEDGE_ARTICLE,
                article.getId(),
                before,
                summary(article)
        );

        return article;
    }

    public KnowledgeArticle archive(
            UUID articleId
    ) {
        KnowledgeArticle article =
                findArticle(articleId);

        String before = summary(article);

        article.archive();

        auditService.record(
                AuditAction.KNOWLEDGE_ARTICLE_ARCHIVED,
                AuditEntityType.KNOWLEDGE_ARTICLE,
                article.getId(),
                before,
                summary(article)
        );

        return article;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticleVersion> getVersions(
            UUID articleId
    ) {
        findArticle(articleId);

        return versionRepository
                .findByArticleIdOrderByVersionNumberDesc(
                        articleId
                );
    }

    private KnowledgeArticle findArticle(
            UUID articleId
    ) {
        return articleRepository
                .findById(articleId)
                .orElseThrow(
                        () ->
                                new KnowledgeArticleNotFoundException(
                                        articleId
                                )
                );
    }

    private String summary(
            KnowledgeArticle article
    ) {
        return "version="
                + article.getVersion()
                + ", status="
                + article.getStatus()
                + ", title="
                + article.getTitle()
                + ", category="
                + article.getCategory()
                + ", ownerId="
                + article.getOwnerId()
                + ", reviewDate="
                + article.getReviewDate();
    }
}
