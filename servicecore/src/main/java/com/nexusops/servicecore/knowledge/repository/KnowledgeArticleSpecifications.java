package com.nexusops.servicecore.knowledge.repository;

import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

public final class KnowledgeArticleSpecifications {

    private KnowledgeArticleSpecifications() {
    }

    public static Specification<KnowledgeArticle> hasStatus(
            KnowledgeArticleStatus status
    ) {
        if (status == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) ->
                builder.equal(
                        root.get("status"),
                        status
                );
    }

    public static Specification<KnowledgeArticle> hasCategory(
            String category
    ) {
        if (category == null || category.isBlank()) {
            return Specification.unrestricted();
        }

        String normalized =
                category.trim()
                        .toLowerCase(Locale.ROOT);

        return (root, query, builder) ->
                builder.equal(
                        builder.lower(
                                root.get("category")
                        ),
                        normalized
                );
    }

    public static Specification<KnowledgeArticle> hasOwnerId(
            String ownerId
    ) {
        if (ownerId == null || ownerId.isBlank()) {
            return Specification.unrestricted();
        }

        return (root, query, builder) ->
                builder.equal(
                        root.get("ownerId"),
                        ownerId.trim()
                );
    }

    public static Specification<KnowledgeArticle> reviewDueBy(
            LocalDate reviewBefore
    ) {
        if (reviewBefore == null) {
            return Specification.unrestricted();
        }

        return (root, query, builder) ->
                builder.lessThanOrEqualTo(
                        root.get("reviewDate"),
                        reviewBefore
                );
    }

    public static Specification<KnowledgeArticle> containsText(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return Specification.unrestricted();
        }

        String normalized =
                escapeLike(
                        text.trim()
                                .toLowerCase(Locale.ROOT)
                );

        String pattern = "%" + normalized + "%";

        return (root, query, builder) ->
                builder.or(
                        builder.like(
                                builder.lower(
                                        root.get("title")
                                ),
                                pattern,
                                '\\'
                        ),
                        builder.like(
                                builder.lower(
                                        root.get("category")
                                ),
                                pattern,
                                '\\'
                        ),
                        builder.like(
                                builder.lower(
                                        root.get("steps")
                                ),
                                pattern,
                                '\\'
                        )
                );
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
