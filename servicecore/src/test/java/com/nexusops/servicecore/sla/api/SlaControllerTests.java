package com.nexusops.servicecore.sla.api;

import com.nexusops.servicecore.sla.application.IncidentSlaNotFoundException;
import com.nexusops.servicecore.sla.application.SlaService;
import com.nexusops.servicecore.sla.domain.IncidentSla;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlaController.class)
class SlaControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlaService slaService;

    @Test
    void getsIncidentSla() throws Exception {
        UUID incidentId = UUID.randomUUID();

        IncidentSla sla = mock(IncidentSla.class);

        when(sla.getIncidentId())
                .thenReturn(incidentId);

        when(sla.getFirstResponseDueAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-17T10:15:00Z"
                        )
                );

        when(sla.getResolutionDueAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-17T14:00:00Z"
                        )
                );

        when(sla.isResponseBreached())
                .thenReturn(false);

        when(sla.isResolutionBreached())
                .thenReturn(false);

        when(slaService.getByIncidentId(incidentId))
                .thenReturn(sla);

        mockMvc.perform(
                        get(
                                "/api/v1/incidents/{incidentId}/sla",
                                incidentId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.incidentId")
                                .value(incidentId.toString())
                )
                .andExpect(
                        jsonPath("$.firstResponseDueAt")
                                .value(
                                        "2026-08-17T10:15:00Z"
                                )
                )
                .andExpect(
                        jsonPath("$.resolutionDueAt")
                                .value(
                                        "2026-08-17T14:00:00Z"
                                )
                )
                .andExpect(
                        jsonPath("$.responseBreached")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.resolutionBreached")
                                .value(false)
                );
    }

    @Test
    void returnsNotFoundForMissingSla() throws Exception {
        UUID incidentId = UUID.randomUUID();

        when(slaService.getByIncidentId(incidentId))
                .thenThrow(
                        new IncidentSlaNotFoundException(
                                incidentId
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/incidents/{incidentId}/sla",
                                incidentId
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.title")
                                .value(
                                        "Incident SLA not found"
                                )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "urn:nexusops:problem:incident-sla-not-found"
                                )
                );
    }
}