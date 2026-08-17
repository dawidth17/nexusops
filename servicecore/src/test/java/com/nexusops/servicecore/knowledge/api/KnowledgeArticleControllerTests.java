package com.nexusops.servicecore.knowledge.api;

import com.nexusops.servicecore.knowledge.application.KnowledgeArticleNotFoundException;
import com.nexusops.servicecore.knowledge.application.KnowledgeArticleService;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticle;
import com.nexusops.servicecore.knowledge.domain.KnowledgeArticleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeArticleController.class)
class KnowledgeArticleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeArticleService articleService;

    @Test
    void createsArticle() throws Exception {
        UUID articleId = UUID.randomUUID();

        KnowledgeArticle article =
                mockArticle(
                        articleId,
                        KnowledgeArticleStatus.DRAFT,
                        1
                );

        when(articleService.create(
                "VPN guide",
                "NETWORK",
                "Restart GlobalProtect.",
                "user-123",
                LocalDate.of(2026, 12, 1)
        )).thenReturn(article);

        mockMvc.perform(
                        post(
                                "/api/v1/knowledge-articles"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "title": "VPN guide",
                                          "category": "NETWORK",
                                          "steps": "Restart GlobalProtect.",
                                          "ownerId": "user-123",
                                          "reviewDate": "2026-12-01"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        articleId.toString()
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("DRAFT")
                )
                .andExpect(
                        jsonPath("$.version")
                                .value(1)
                );
    }

    @Test
    void publishesArticle() throws Exception {
        UUID articleId = UUID.randomUUID();

        KnowledgeArticle article =
                mockArticle(
                        articleId,
                        KnowledgeArticleStatus.PUBLISHED,
                        1
                );

        when(articleService.publish(articleId))
                .thenReturn(article);

        mockMvc.perform(
                        post(
                                "/api/v1/knowledge-articles/{articleId}/publish",
                                articleId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("PUBLISHED")
                );
    }

    @Test
    void returnsNotFoundForMissingArticle()
            throws Exception {

        UUID articleId = UUID.randomUUID();

        when(articleService.getById(articleId))
                .thenThrow(
                        new KnowledgeArticleNotFoundException(
                                articleId
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/knowledge-articles/{articleId}",
                                articleId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Knowledge article not found"
                                )
                );
    }

    private KnowledgeArticle mockArticle(
            UUID id,
            KnowledgeArticleStatus status,
            int version
    ) {
        KnowledgeArticle article =
                mock(KnowledgeArticle.class);

        when(article.getId()).thenReturn(id);
        when(article.getTitle()).thenReturn("VPN guide");
        when(article.getCategory()).thenReturn("NETWORK");
        when(article.getSteps())
                .thenReturn("Restart GlobalProtect.");
        when(article.getStatus()).thenReturn(status);
        when(article.getOwnerId()).thenReturn("user-123");
        when(article.getReviewDate())
                .thenReturn(
                        LocalDate.of(
                                2026,
                                12,
                                1
                        )
                );
        when(article.getVersion()).thenReturn(version);

        return article;
    }
}