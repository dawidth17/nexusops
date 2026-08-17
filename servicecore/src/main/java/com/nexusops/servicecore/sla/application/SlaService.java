package com.nexusops.servicecore.sla.application;

import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.sla.domain.IncidentSla;
import com.nexusops.servicecore.sla.domain.SlaDeadlineCalculator;
import com.nexusops.servicecore.sla.domain.SlaDeadlines;
import com.nexusops.servicecore.sla.domain.SlaPolicy;
import com.nexusops.servicecore.sla.repository.IncidentSlaRepository;
import com.nexusops.servicecore.sla.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SlaService {

    private final SlaPolicyRepository slaPolicyRepository;
    private final IncidentSlaRepository incidentSlaRepository;
    private final SlaDeadlineCalculator deadlineCalculator;

    public SlaService(
            SlaPolicyRepository slaPolicyRepository,
            IncidentSlaRepository incidentSlaRepository
    ) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.incidentSlaRepository = incidentSlaRepository;
        this.deadlineCalculator = new SlaDeadlineCalculator();
    }

    public IncidentSla createForIncident(Incident incident) {
        if (incident == null) {
            throw new IllegalArgumentException(
                    "incident must not be null"
            );
        }

        if (incident.getCreatedAt() == null) {
            throw new IllegalArgumentException(
                    "incident must be persisted before SLA creation"
            );
        }

        SlaPolicy policy = slaPolicyRepository
                .findById(incident.getPriority())
                .orElseThrow(
                        () -> new SlaPolicyNotFoundException(
                                incident.getPriority()
                        )
                );

        SlaDeadlines deadlines = deadlineCalculator.calculate(
                incident.getCreatedAt(),
                policy
        );

        IncidentSla incidentSla = IncidentSla.create(
                incident,
                deadlines.firstResponseDueAt(),
                deadlines.resolutionDueAt()
        );

        return incidentSlaRepository.save(incidentSla);
    }

    @Transactional(readOnly = true)
    public IncidentSla getByIncidentId(UUID incidentId) {
        return findIncidentSla(incidentId);
    }

    public IncidentSla markFirstResponse(
            UUID incidentId,
            Instant respondedAt
    ) {
        IncidentSla incidentSla = findIncidentSla(
                incidentId
        );

        incidentSla.markFirstResponse(respondedAt);

        return incidentSla;
    }

    public IncidentSla markResolved(
            UUID incidentId,
            Instant resolvedAt
    ) {
        IncidentSla incidentSla = findIncidentSla(
                incidentId
        );

        incidentSla.markResolved(resolvedAt);

        return incidentSla;
    }

    public IncidentSla markReopened(UUID incidentId) {
        IncidentSla incidentSla = findIncidentSla(
                incidentId
        );

        incidentSla.markReopened();

        return incidentSla;
    }

    private IncidentSla findIncidentSla(UUID incidentId) {
        return incidentSlaRepository
                .findById(incidentId)
                .orElseThrow(
                        () -> new IncidentSlaNotFoundException(
                                incidentId
                        )
                );
    }
}
