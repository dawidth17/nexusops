package com.nexusops.servicecore.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "knowledge_article_versions")
public class KnowledgeArticleVersion {

    @Id
    private UUID id;

    @Column(
            name = "article_id",
            nullable = false
    )
    private UUID articleId;

    @Column(
            name = "version_number",
            nullable = false
    )
    private int versionNumber;

    @Column(
            nullable = false,
            length = 200
    )
    private String title;

    @Column(
            nullable = false,
            length = 100
    )
    private String category;

    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String steps;

    @Column(
            name = "owner_id",
            nullable = false,
            length = 255
    )
    private String ownerId;

    @Column(
            name = "review_date",
            nullable = false
    )
    private LocalDate reviewDate;

    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    protected KnowledgeArticleVersion() {
    }

    private KnowledgeArticleVersion(
            KnowledgeArticle article,
            Instant createdAt
    ) {
        if (article == null) {
            throw new IllegalArgumentException(
                    "article must not be null"
            );
        }

        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "createdAt must not be null"
            );
        }

        id = UUID.randomUUID();
        articleId = article.getId();
        versionNumber = article.getVersion();
        title = article.getTitle();
        category = article.getCategory();
        steps = article.getSteps();
        ownerId = article.getOwnerId();
        reviewDate = article.getReviewDate();
        this.createdAt = createdAt;
    }

    public static KnowledgeArticleVersion snapshot(
            KnowledgeArticle article,
            Instant createdAt
    ) {
        return new KnowledgeArticleVersion(
                article,
                createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getArticleId() {
        return articleId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getSteps() {
        return steps;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
