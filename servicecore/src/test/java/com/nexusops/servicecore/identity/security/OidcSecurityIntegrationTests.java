package com.nexusops.servicecore.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = "servicecore.kafka.enabled=false"
)
@AutoConfigureMockMvc
@ActiveProfiles("oidc")
@Transactional
class OidcSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void allowsHealthWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/health")
                )
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedApiRequest()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/assets")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void allowsEmployeeToReadAssets()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/assets")
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "EMPLOYEE"
                                                        )
                                                )
                                )
                )
                .andExpect(status().isOk());
    }

    @Test
    void allowsEmployeeToReachIncidentCreation()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/incidents")
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "EMPLOYEE"
                                                        )
                                                )
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    @Test
    void rejectsEmployeeAssetAssignment()
            throws Exception {

        UUID assetId = UUID.randomUUID();

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/assign",
                                assetId
                        )
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "EMPLOYEE"
                                                        )
                                                )
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content("""
                                        {
                                          "assigneeId": "user-123"
                                        }
                                        """)
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void allowsTechnicianAssetAssignment()
            throws Exception {

        UUID assetId = UUID.randomUUID();

        mockMvc.perform(
                        post(
                                "/api/v1/assets/{assetId}/assign",
                                assetId
                        )
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "TECHNICIAN"
                                                        )
                                                )
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content("""
                                        {
                                          "assigneeId": "user-123"
                                        }
                                        """)
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void rejectsTechnicianCreatingAsset()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/assets")
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "TECHNICIAN"
                                                        )
                                                )
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content("""
                                        {
                                          "assetTag": "LAP-SEC-001",
                                          "type": "LAPTOP",
                                          "manufacturer": "Dell",
                                          "model": "Latitude",
                                          "serialNumber": "SEC-001"
                                        }
                                        """)
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void rejectsTechnicianPublishingKnowledgeArticle()
            throws Exception {

        UUID articleId = UUID.randomUUID();

        mockMvc.perform(
                        post(
                                "/api/v1/knowledge-articles/{articleId}/publish",
                                articleId
                        )
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "TECHNICIAN"
                                                        )
                                                )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void allowsManagerToReachKnowledgePublishing()
            throws Exception {

        UUID articleId = UUID.randomUUID();

        mockMvc.perform(
                        post(
                                "/api/v1/knowledge-articles/{articleId}/publish",
                                articleId
                        )
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "MANAGER"
                                                        )
                                                )
                                )
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void rejectsEmployeeReadingAudit()
            throws Exception {

        UUID entityId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/audit/ASSET/{entityId}",
                                entityId
                        )
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "EMPLOYEE"
                                                        )
                                                )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void allowsManagerToReadAudit()
            throws Exception {

        UUID entityId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/audit/ASSET/{entityId}",
                                entityId
                        )
                                .with(
                                        jwt()
                                                .authorities(
                                                        role(
                                                                "MANAGER"
                                                        )
                                                )
                                )
                )
                .andExpect(status().isOk());
    }

    @Test
    void exposesCurrentOidcUser()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/me")
                                .with(
                                        jwt()
                                                .jwt(
                                                        jwt ->
                                                                jwt.subject(
                                                                        "keycloak-user-123"
                                                                )
                                                                        .claim(
                                                                                "preferred_username",
                                                                                "alice"
                                                                        )
                                                )
                                                .authorities(
                                                        role(
                                                                "TECHNICIAN"
                                                        )
                                                )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.subjectId")
                                .value(
                                        "keycloak-user-123"
                                )
                )
                .andExpect(
                        jsonPath("$.username")
                                .value("alice")
                )
                .andExpect(
                        jsonPath("$.roles[0]")
                                .value(
                                        "TECHNICIAN"
                                )
                );
    }

    private SimpleGrantedAuthority role(
            String role
    ) {
        return new SimpleGrantedAuthority(
                "ROLE_" + role
        );
    }
}
