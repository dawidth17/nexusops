package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.application.IncidentService;
import com.nexusops.servicecore.incident.domain.Incident;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(
            @Valid @RequestBody CreateIncidentRequest request
    ) {
        Incident incident = incidentService.create(
                request.title(),
                request.description(),
                request.impact(),
                request.urgency()
        );

        URI location = URI.create(
                "/api/v1/incidents/" + incident.getId()
        );

        return ResponseEntity
                .created(location)
                .body(IncidentResponse.from(incident));
    }

    @GetMapping("/{incidentId}")
    public IncidentResponse getById(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.getById(incidentId);

        return IncidentResponse.from(incident);
    }

    @PatchMapping("/{incidentId}/assessment")
    public IncidentResponse updateAssessment(
            @PathVariable UUID incidentId,
            @Valid @RequestBody UpdateIncidentAssessmentRequest request
    ) {
        Incident incident = incidentService.updateAssessment(
                incidentId,
                request.impact(),
                request.urgency()
        );

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/start-progress")
    public IncidentResponse startProgress(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.startProgress(incidentId);

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/return-to-open")
    public IncidentResponse returnToOpen(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.returnToOpen(incidentId);

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/resolve")
    public IncidentResponse resolve(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.resolve(incidentId);

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/reopen")
    public IncidentResponse reopen(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.reopen(incidentId);

        return IncidentResponse.from(incident);
    }

    @PostMapping("/{incidentId}/close")
    public IncidentResponse close(
            @PathVariable UUID incidentId
    ) {
        Incident incident = incidentService.close(incidentId);

        return IncidentResponse.from(incident);
    }
}