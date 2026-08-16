package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.application.IncidentCommentService;
import com.nexusops.servicecore.incident.application.IncidentNotFoundException;
import com.nexusops.servicecore.incident.application.IncidentSearchCriteria;
import com.nexusops.servicecore.incident.application.IncidentService;
import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentComment;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.InvalidIncidentTransitionException;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;

    @MockitoBean
    private IncidentCommentService incidentCommentService;

    @Test
    void createsIncident() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.HIGH,
                Urgency.HIGH,
                Priority.P1,
                IncidentStatus.OPEN
        );

        when(incidentService.create(
                "Email service unavailable",
                "Multiple users cannot access email",
                Impact.HIGH,
                Urgency.HIGH
        )).thenReturn(incident);

        mockMvc.perform(
                        post("/api/v1/incidents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "Email service unavailable",
                                          "description": "Multiple users cannot access email",
                                          "impact": "HIGH",
                                          "urgency": "HIGH"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                "/api/v1/incidents/" + incidentId
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(incidentId.toString())
                )
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void searchesIncidentsWithDefaultPagination() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.MEDIUM,
                Urgency.HIGH,
                Priority.P2,
                IncidentStatus.OPEN
        );

        Page<Incident> incidentPage = new PageImpl<>(
                List.of(incident)
        );

        when(incidentService.search(
                any(IncidentSearchCriteria.class),
                any(Pageable.class)
        )).thenReturn(incidentPage);

        mockMvc.perform(
                        get("/api/v1/incidents")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(incidentId.toString())
                )
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        ArgumentCaptor<IncidentSearchCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(
                        IncidentSearchCriteria.class
                );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(incidentService).search(
                criteriaCaptor.capture(),
                pageableCaptor.capture()
        );

        IncidentSearchCriteria criteria =
                criteriaCaptor.getValue();

        assertEquals(null, criteria.status());
        assertEquals(null, criteria.priority());
        assertEquals(null, criteria.impact());
        assertEquals(null, criteria.urgency());
        assertEquals(null, criteria.teamId());
        assertEquals(null, criteria.assigneeId());
        assertEquals(null, criteria.text());

        Pageable pageable = pageableCaptor.getValue();

        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());

        Sort.Order createdAtOrder =
                pageable.getSort().getOrderFor("createdAt");

        assertNotNull(createdAtOrder);
        assertEquals(
                Sort.Direction.DESC,
                createdAtOrder.getDirection()
        );
    }

    @Test
    void passesIncidentSearchFiltersToService() throws Exception {
        when(incidentService.search(
                any(IncidentSearchCriteria.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        mockMvc.perform(
                        get("/api/v1/incidents")
                                .param("status", "OPEN")
                                .param("priority", "P2")
                                .param("impact", "MEDIUM")
                                .param("urgency", "HIGH")
                                .param(
                                        "teamId",
                                        "network-operations"
                                )
                                .param("assigneeId", "user-123")
                                .param("q", "network")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        ArgumentCaptor<IncidentSearchCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(
                        IncidentSearchCriteria.class
                );

        verify(incidentService).search(
                criteriaCaptor.capture(),
                any(Pageable.class)
        );

        IncidentSearchCriteria criteria =
                criteriaCaptor.getValue();

        assertEquals(
                IncidentStatus.OPEN,
                criteria.status()
        );
        assertEquals(Priority.P2, criteria.priority());
        assertEquals(Impact.MEDIUM, criteria.impact());
        assertEquals(Urgency.HIGH, criteria.urgency());
        assertEquals(
                "network-operations",
                criteria.teamId()
        );
        assertEquals(
                "user-123",
                criteria.assigneeId()
        );
        assertEquals("network", criteria.text());
    }

    @Test
    void usesCustomPaginationAndSorting() throws Exception {
        when(incidentService.search(
                any(IncidentSearchCriteria.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        mockMvc.perform(
                        get("/api/v1/incidents")
                                .param("page", "1")
                                .param("size", "5")
                                .param("sort", "title,asc")
                )
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(incidentService).search(
                any(IncidentSearchCriteria.class),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();

        assertEquals(1, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());

        Sort.Order titleOrder =
                pageable.getSort().getOrderFor("title");

        assertNotNull(titleOrder);
        assertEquals(
                Sort.Direction.ASC,
                titleOrder.getDirection()
        );
    }

    @Test
    void rejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(
                        get("/api/v1/incidents")
                                .param("size", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(incidentService);
    }

    @Test
    void rejectsUnsupportedSortField() throws Exception {
        mockMvc.perform(
                        get("/api/v1/incidents")
                                .param(
                                        "sort",
                                        "unknownField,desc"
                                )
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(incidentService);
    }

    @Test
    void getsIncidentById() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.MEDIUM,
                Urgency.HIGH,
                Priority.P2,
                IncidentStatus.OPEN
        );

        when(incidentService.getById(incidentId))
                .thenReturn(incident);

        mockMvc.perform(
                        get(
                                "/api/v1/incidents/{incidentId}",
                                incidentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(incidentId.toString())
                )
                .andExpect(
                        jsonPath("$.impact")
                                .value("MEDIUM")
                )
                .andExpect(
                        jsonPath("$.urgency")
                                .value("HIGH")
                )
                .andExpect(
                        jsonPath("$.priority")
                                .value("P2")
                );
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/incidents")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "title": "",
                                          "description": "Test description",
                                          "impact": "HIGH",
                                          "urgency": "HIGH"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.title")
                                .value("Invalid request")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.errors.title")
                                .exists()
                );

        verifyNoInteractions(incidentService);
    }

    @Test
    void returnsNotFoundForMissingIncident() throws Exception {
        UUID incidentId = UUID.randomUUID();

        when(incidentService.getById(incidentId))
                .thenThrow(
                        new IncidentNotFoundException(
                                incidentId
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/incidents/{incidentId}",
                                incidentId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value("Incident not found")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:nexusops:problem:incident-not-found"
                                )
                );
    }

    @Test
    void updatesIncidentAssessment() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.HIGH,
                Urgency.HIGH,
                Priority.P1,
                IncidentStatus.OPEN
        );

        when(incidentService.updateAssessment(
                incidentId,
                Impact.HIGH,
                Urgency.HIGH
        )).thenReturn(incident);

        mockMvc.perform(
                        patch(
                                "/api/v1/incidents/{incidentId}/assessment",
                                incidentId
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "impact": "HIGH",
                                          "urgency": "HIGH"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.impact")
                                .value("HIGH")
                )
                .andExpect(
                        jsonPath("$.urgency")
                                .value("HIGH")
                )
                .andExpect(
                        jsonPath("$.priority")
                                .value("P1")
                );
    }

    @Test
    void assignsIncidentToTeam() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                Priority.P3,
                IncidentStatus.OPEN,
                null,
                "network-operations"
        );

        when(incidentService.assignToTeam(
                incidentId,
                "network-operations"
        )).thenReturn(incident);

        mockMvc.perform(
                        patch(
                                "/api/v1/incidents/{incidentId}/assignment/team",
                                incidentId
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "teamId": "network-operations"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.teamId")
                                .value("network-operations")
                );
    }

    @Test
    void assignsIncidentToUser() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                Priority.P3,
                IncidentStatus.OPEN,
                "user-123",
                "network-operations"
        );

        when(incidentService.assignToUser(
                incidentId,
                "user-123"
        )).thenReturn(incident);

        mockMvc.perform(
                        patch(
                                "/api/v1/incidents/{incidentId}/assignment/assignee",
                                incidentId
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "assigneeId": "user-123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.assigneeId")
                                .value("user-123")
                )
                .andExpect(
                        jsonPath("$.teamId")
                                .value("network-operations")
                );
    }

    @Test
    void rejectsBlankTeamAssignment() throws Exception {
        UUID incidentId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/incidents/{incidentId}/assignment/team",
                                incidentId
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "teamId": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.title")
                                .value("Invalid request")
                )
                .andExpect(
                        jsonPath("$.errors.teamId")
                                .exists()
                );

        verifyNoInteractions(incidentService);
    }

    @Test
    void clearsIncidentAssignee() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                Priority.P3,
                IncidentStatus.OPEN,
                null,
                "network-operations"
        );

        when(incidentService.clearAssignee(incidentId))
                .thenReturn(incident);

        mockMvc.perform(
                        delete(
                                "/api/v1/incidents/{incidentId}/assignment/assignee",
                                incidentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.assigneeId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.teamId")
                                .value("network-operations")
                );
    }

    @Test
    void clearsIncidentTeam() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                Priority.P3,
                IncidentStatus.OPEN,
                "user-123",
                null
        );

        when(incidentService.clearTeam(incidentId))
                .thenReturn(incident);

        mockMvc.perform(
                        delete(
                                "/api/v1/incidents/{incidentId}/assignment/team",
                                incidentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.teamId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.assigneeId")
                                .value("user-123")
                );
    }

    @Test
    void addsCommentToIncident() throws Exception {
        UUID incidentId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        IncidentComment comment = mockComment(
                commentId,
                incidentId,
                "user-123",
                "The network team is investigating."
        );

        when(incidentCommentService.addComment(
                incidentId,
                "user-123",
                "The network team is investigating."
        )).thenReturn(comment);

        mockMvc.perform(
                        post(
                                "/api/v1/incidents/{incidentId}/comments",
                                incidentId
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "authorId": "user-123",
                                          "content": "The network team is investigating."
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id")
                                .value(commentId.toString())
                )
                .andExpect(
                        jsonPath("$.incidentId")
                                .value(incidentId.toString())
                )
                .andExpect(
                        jsonPath("$.authorId")
                                .value("user-123")
                )
                .andExpect(
                        jsonPath("$.content")
                                .value(
                                        "The network team is investigating."
                                )
                );
    }

    @Test
    void listsIncidentComments() throws Exception {
        UUID incidentId = UUID.randomUUID();

        IncidentComment firstComment = mockComment(
                UUID.randomUUID(),
                incidentId,
                "user-123",
                "First comment"
        );

        IncidentComment secondComment = mockComment(
                UUID.randomUUID(),
                incidentId,
                "user-456",
                "Second comment"
        );

        when(incidentCommentService.listComments(incidentId))
                .thenReturn(
                        List.of(
                                firstComment,
                                secondComment
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/incidents/{incidentId}/comments",
                                incidentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].content")
                                .value("First comment")
                )
                .andExpect(
                        jsonPath("$[1].content")
                                .value("Second comment")
                );
    }

    @Test
    void rejectsInvalidCommentRequest() throws Exception {
        UUID incidentId = UUID.randomUUID();

        mockMvc.perform(
                        post(
                                "/api/v1/incidents/{incidentId}/comments",
                                incidentId
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "authorId": "user-123",
                                          "content": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.title")
                                .value("Invalid request")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.errors.content")
                                .exists()
                );

        verifyNoInteractions(incidentCommentService);
    }

    @Test
    void startsIncidentProgress() throws Exception {
        UUID incidentId = UUID.randomUUID();

        Incident incident = mockIncident(
                incidentId,
                Impact.MEDIUM,
                Urgency.MEDIUM,
                Priority.P3,
                IncidentStatus.IN_PROGRESS
        );

        when(incidentService.startProgress(incidentId))
                .thenReturn(incident);

        mockMvc.perform(
                        post(
                                "/api/v1/incidents/{incidentId}/start-progress",
                                incidentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("IN_PROGRESS")
                );
    }

    @Test
    void returnsConflictForInvalidTransition() throws Exception {
        UUID incidentId = UUID.randomUUID();

        when(incidentService.reopen(incidentId))
                .thenThrow(
                        new InvalidIncidentTransitionException(
                                IncidentStatus.CLOSED,
                                IncidentStatus.IN_PROGRESS
                        )
                );

        mockMvc.perform(
                        post(
                                "/api/v1/incidents/{incidentId}/reopen",
                                incidentId
                        )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Invalid incident transition"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:nexusops:problem:invalid-incident-transition"
                                )
                );
    }

    private Incident mockIncident(
            UUID incidentId,
            Impact impact,
            Urgency urgency,
            Priority priority,
            IncidentStatus status
    ) {
        return mockIncident(
                incidentId,
                impact,
                urgency,
                priority,
                status,
                null,
                null
        );
    }

    private Incident mockIncident(
            UUID incidentId,
            Impact impact,
            Urgency urgency,
            Priority priority,
            IncidentStatus status,
            String assigneeId,
            String teamId
    ) {
        Incident incident = mock(Incident.class);

        when(incident.getId()).thenReturn(incidentId);
        when(incident.getTitle()).thenReturn("Test incident");
        when(incident.getDescription())
                .thenReturn("Test incident description");
        when(incident.getImpact()).thenReturn(impact);
        when(incident.getUrgency()).thenReturn(urgency);
        when(incident.getPriority()).thenReturn(priority);
        when(incident.getStatus()).thenReturn(status);
        when(incident.getAssigneeId())
                .thenReturn(assigneeId);
        when(incident.getTeamId()).thenReturn(teamId);
        when(incident.getCreatedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-16T12:00:00Z"
                        )
                );
        when(incident.getUpdatedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-16T12:00:00Z"
                        )
                );

        return incident;
    }

    private IncidentComment mockComment(
            UUID commentId,
            UUID incidentId,
            String authorId,
            String content
    ) {
        Incident incident = mock(Incident.class);
        IncidentComment comment =
                mock(IncidentComment.class);

        when(incident.getId()).thenReturn(incidentId);

        when(comment.getId()).thenReturn(commentId);
        when(comment.getIncident()).thenReturn(incident);
        when(comment.getAuthorId()).thenReturn(authorId);
        when(comment.getContent()).thenReturn(content);
        when(comment.getCreatedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-16T12:00:00Z"
                        )
                );

        return comment;
    }
}