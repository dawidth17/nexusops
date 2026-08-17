package com.nexusops.servicecore.sla.api;

import com.nexusops.servicecore.sla.application.SlaService;
import com.nexusops.servicecore.sla.domain.IncidentSla;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/sla")
public class SlaController {

    private final SlaService slaService;

    public SlaController(SlaService slaService) {
        this.slaService = slaService;
    }

    @GetMapping
    public SlaResponse getSla(
            @PathVariable UUID incidentId
    ) {
        IncidentSla sla =
                slaService.getByIncidentId(incidentId);

        return SlaResponse.from(sla);
    }
}
