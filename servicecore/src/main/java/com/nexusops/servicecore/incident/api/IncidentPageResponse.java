package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.domain.Incident;
import org.springframework.data.domain.Page;

import java.util.List;

public record IncidentPageResponse(
        List<IncidentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static IncidentPageResponse from(
            Page<Incident> incidentPage
    ) {
        List<IncidentResponse> content = incidentPage
                .getContent()
                .stream()
                .map(IncidentResponse::from)
                .toList();

        return new IncidentPageResponse(
                content,
                incidentPage.getNumber(),
                incidentPage.getSize(),
                incidentPage.getTotalElements(),
                incidentPage.getTotalPages(),
                incidentPage.isFirst(),
                incidentPage.isLast()
        );
    }
}