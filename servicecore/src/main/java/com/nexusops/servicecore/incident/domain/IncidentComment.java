package com.nexusops.servicecore.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_comments")
public class IncidentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "author_id", nullable = false, length = 255)
    private String authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncidentComment() {
    }

    private IncidentComment(
            Incident incident,
            String authorId,
            String content
    ) {
        if (incident == null) {
            throw new IllegalArgumentException(
                    "incident must not be null"
            );
        }

        this.incident = incident;
        this.authorId = requireText(authorId, "authorId");
        this.content = requireText(content, "content");
    }

    public static IncidentComment create(
            Incident incident,
            String authorId,
            String content
    ) {
        return new IncidentComment(
                incident,
                authorId,
                content
        );
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
