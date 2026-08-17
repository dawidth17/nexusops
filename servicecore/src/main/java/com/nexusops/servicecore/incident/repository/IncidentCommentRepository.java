package com.nexusops.servicecore.incident.repository;

import com.nexusops.servicecore.incident.domain.IncidentComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentCommentRepository
        extends JpaRepository<IncidentComment, UUID> {

    List<IncidentComment> findByIncident_IdOrderByCreatedAtAsc(
            UUID incidentId
    );
}
