package com.nexusops.servicecore.knowledge.api;

import com.nexusops.servicecore.knowledge.application.KnowledgeArticleSearchCriteria;
import com.nexusops.servicecore.knowledge.application.KnowledgeArticleService;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge-articles")
public class KnowledgeArticleController {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "title",
                    "category",
                    "status",
                    "ownerId",
                    "reviewDate",
                    "version",
                    "createdAt",
                    "updatedAt"
            );

    private final KnowledgeArticleService articleService;

    public KnowledgeArticleController(
            KnowledgeArticleService articleService
    ) {
        this.articleService = articleService;
    }

    @PostMapping
    public ResponseEntity<KnowledgeArticleResponse> create(
            @Valid
            @RequestBody
            CreateKnowledgeArticleRequest request
    ) {
        KnowledgeArticle article =
                articleService.create(
                        request.title(),
                        request.category(),
                        request.steps(),
                        request.ownerId(),
                        request.reviewDate()
                );

        URI location = URI.create(
                "/api/v1/knowledge-articles/"
                        + article.getId()
        );

        return ResponseEntity
                .created(location)
                .body(
                        KnowledgeArticleResponse.from(
                                article
                        )
                );
    }

    @GetMapping
    public KnowledgeArticlePageResponse search(
            @RequestParam(required = false)
            KnowledgeArticleStatus status,

            @RequestParam(required = false)
            String category,

            @RequestParam(required = false)
            String ownerId,

            @RequestParam(required = false)
            LocalDate reviewBefore,

            @RequestParam(name = "q", required = false)
            String query,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(defaultValue = "updatedAt,desc")
            String sort
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 100"
            );
        }

        KnowledgeArticleSearchCriteria criteria =
                new KnowledgeArticleSearchCriteria(
                        status,
                        category,
                        ownerId,
                        reviewBefore,
                        query
                );

        Page<KnowledgeArticle> articles =
                articleService.search(
                        criteria,
                        PageRequest.of(
                                page,
                                size,
                                parseSort(sort)
                        )
                );

        return KnowledgeArticlePageResponse.from(
                articles
        );
    }

    @GetMapping("/{articleId}")
    public KnowledgeArticleResponse getById(
            @PathVariable UUID articleId
    ) {
        return KnowledgeArticleResponse.from(
                articleService.getById(articleId)
        );
    }

    @PutMapping("/{articleId}")
    public KnowledgeArticleResponse update(
            @PathVariable UUID articleId,
            @Valid
            @RequestBody
            UpdateKnowledgeArticleRequest request
    ) {
        return KnowledgeArticleResponse.from(
                articleService.update(
                        articleId,
                        request.title(),
                        request.category(),
                        request.steps(),
                        request.ownerId(),
                        request.reviewDate()
                )
        );
    }

    @PostMapping("/{articleId}/publish")
    public KnowledgeArticleResponse publish(
            @PathVariable UUID articleId
    ) {
        return KnowledgeArticleResponse.from(
                articleService.publish(articleId)
        );
    }

    @PostMapping("/{articleId}/archive")
    public KnowledgeArticleResponse archive(
            @PathVariable UUID articleId
    ) {
        return KnowledgeArticleResponse.from(
                articleService.archive(articleId)
        );
    }

    @GetMapping("/{articleId}/versions")
    public List<KnowledgeArticleVersionResponse> versions(
            @PathVariable UUID articleId
    ) {
        return articleService
                .getVersions(articleId)
                .stream()
                .map(
                        KnowledgeArticleVersionResponse::from
                )
                .toList();
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "sort must use field,direction format"
            );
        }

        String field = parts[0].trim();
        String direction = parts[1].trim();

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException(
                    "unsupported sort field: "
                            + field
            );
        }

        return Sort.by(
                Sort.Direction.fromString(direction),
                field
        );
    }
}
