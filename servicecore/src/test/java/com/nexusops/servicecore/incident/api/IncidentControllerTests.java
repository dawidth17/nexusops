package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.application.IncidentNotFoundException;
import com.nexusops.servicecore.incident.application.IncidentService;
import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentStatus;
import com.nexusops.servicecore.incident.domain.InvalidIncidentTransitionException;
import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.incident.domain.Urgency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
                .andExpect(jsonPath("$.id").value(incidentId.toString()))
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
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
                        get("/api/v1/incidents/{incidentId}", incidentId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incidentId.toString()))
                .andExpect(jsonPath("$.impact").value("MEDIUM"))
                .andExpect(jsonPath("$.urgency").value("HIGH"))
                .andExpect(jsonPath("$.priority").value("P2"));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/incidents")
                                .contentType(MediaType.APPLICATION_JSON)
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
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.title").exists());

        verifyNoInteractions(incidentService);
    }

    @Test
    void returnsNotFoundForMissingIncident() throws Exception {
        UUID incidentId = UUID.randomUUID();

        when(incidentService.getById(incidentId))
                .thenThrow(new IncidentNotFoundException(incidentId));

        mockMvc.perform(
                        get("/api/v1/incidents/{incidentId}", incidentId)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Incident not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.type")
                                .value("urn:nexusops:problem:incident-not-found")
                );
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
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
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
                                .value("Invalid incident transition")
                )
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:nexusops:problem:invalid-incident-transition"
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
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "impact": "HIGH",
                                          "urgency": "HIGH"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impact").value("HIGH"))
                .andExpect(jsonPath("$.urgency").value("HIGH"))
                .andExpect(jsonPath("$.priority").value("P1"));
    }

    private Incident mockIncident(
            UUID incidentId,
            Impact impact,
            Urgency urgency,
            Priority priority,
            IncidentStatus status
    ) {
        Incident incident = mock(Incident.class);

        when(incident.getId()).thenReturn(incidentId);
        when(incident.getTitle()).thenReturn("Test incident");
        when(incident.getDescription()).thenReturn("Test incident description");
        when(incident.getImpact()).thenReturn(impact);
        when(incident.getUrgency()).thenReturn(urgency);
        when(incident.getPriority()).thenReturn(priority);
        when(incident.getStatus()).thenReturn(status);
        when(incident.getCreatedAt())
                .thenReturn(Instant.parse("2026-08-16T12:00:00Z"));
        when(incident.getUpdatedAt())
                .thenReturn(Instant.parse("2026-08-16T12:00:00Z"));

        return incident;
    }
}