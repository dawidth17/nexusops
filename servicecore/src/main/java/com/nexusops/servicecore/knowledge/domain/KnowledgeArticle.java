package com.nexusops.servicecore.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "knowledge_articles")
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private KnowledgeArticleStatus status;

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
            nullable = false
    )
    private int version;

    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected KnowledgeArticle() {
    }

    private KnowledgeArticle(
            String title,
            String category,
            String steps,
            String ownerId,
            LocalDate reviewDate
    ) {
        this.title = requireText(
                title,
                "title"
        );

        this.category = requireText(
                category,
                "category"
        );

        this.steps = requireText(
                steps,
                "steps"
        );

        this.ownerId = requireText(
                ownerId,
                "ownerId"
        );

        if (reviewDate == null) {
            throw new IllegalArgumentException(
                    "reviewDate must not be null"
            );
        }

        this.reviewDate = reviewDate;
        status = KnowledgeArticleStatus.DRAFT;
        version = 1;
    }

    public static KnowledgeArticle create(
            String title,
            String category,
            String steps,
            String ownerId,
            LocalDate reviewDate
    ) {
        return new KnowledgeArticle(
                title,
                category,
                steps,
                ownerId,
                reviewDate
        );
    }

    public void update(
            String title,
            String category,
            String steps,
            String ownerId,
            LocalDate reviewDate
    ) {
        if (status == KnowledgeArticleStatus.ARCHIVED) {
            throw new InvalidKnowledgeArticleOperationException(
                    "archived articles cannot be edited"
            );
        }

        this.title = requireText(
                title,
                "title"
        );

        this.category = requireText(
                category,
                "category"
        );

        this.steps = requireText(
                steps,
                "steps"
        );

        this.ownerId = requireText(
                ownerId,
                "ownerId"
        );

        if (reviewDate == null) {
            throw new IllegalArgumentException(
                    "reviewDate must not be null"
            );
        }

        this.reviewDate = reviewDate;
        version++;
    }

    public void publish() {
        if (status != KnowledgeArticleStatus.DRAFT) {
            throw new InvalidKnowledgeArticleOperationException(
                    "only draft articles can be published"
            );
        }

        status = KnowledgeArticleStatus.PUBLISHED;
    }

    public void archive() {
        if (status == KnowledgeArticleStatus.ARCHIVED) {
            return;
        }

        status = KnowledgeArticleStatus.ARCHIVED;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    public UUID getId() {
        return id;
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

    public KnowledgeArticleStatus getStatus() {
        return status;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
