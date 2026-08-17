package com.nexusops.servicecore.incident.api;

import com.nexusops.servicecore.incident.domain.IncidentComment;

import java.time.Instant;
import java.util.UUID;

public record IncidentCommentResponse(
        UUID id,
        UUID incidentId,
        String authorId,
        String content,
        Instant createdAt
) {

    public static IncidentCommentResponse from(
            IncidentComment comment
    ) {
        return new IncidentCommentResponse(
                comment.getId(),
                comment.getIncident().getId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
