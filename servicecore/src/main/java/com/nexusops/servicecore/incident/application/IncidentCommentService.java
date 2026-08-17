package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentComment;
import com.nexusops.servicecore.incident.repository.IncidentCommentRepository;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class IncidentCommentService {

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository incidentCommentRepository;

    public IncidentCommentService(
            IncidentRepository incidentRepository,
            IncidentCommentRepository incidentCommentRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.incidentCommentRepository = incidentCommentRepository;
    }

    public IncidentComment addComment(
            UUID incidentId,
            String authorId,
            String content
    ) {
        Incident incident = findIncident(incidentId);

        IncidentComment comment = IncidentComment.create(
                incident,
                authorId,
                content
        );

        return incidentCommentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    public List<IncidentComment> listComments(UUID incidentId) {
        findIncident(incidentId);

        return incidentCommentRepository
                .findByIncident_IdOrderByCreatedAtAsc(incidentId);
    }

    private Incident findIncident(UUID incidentId) {
        return incidentRepository
                .findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }
}
