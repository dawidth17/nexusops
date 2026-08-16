package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import com.nexusops.servicecore.incident.repository.IncidentSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(
            IncidentRepository incidentRepository
    ) {
        this.incidentRepository = incidentRepository;
    }

    public Incident create(
            String title,
            String description,
            Impact impact,
            Urgency urgency
    ) {
        Incident incident = Incident.create(
                title,
                description,
                impact,
                urgency
        );

        return incidentRepository.save(incident);
    }

    @Transactional(readOnly = true)
    public Incident getById(UUID incidentId) {
        return findIncident(incidentId);
    }

    @Transactional(readOnly = true)
    public Page<Incident> search(
            IncidentSearchCriteria criteria,
            Pageable pageable
    ) {
        Objects.requireNonNull(
                criteria,
                "criteria must not be null"
        );

        Objects.requireNonNull(
                pageable,
                "pageable must not be null"
        );

        Specification<Incident> specification =
                Specification.unrestricted();

        specification = specification
                .and(
                        IncidentSpecifications.hasStatus(
                                criteria.status()
                        )
                )
                .and(
                        IncidentSpecifications.hasPriority(
                                criteria.priority()
                        )
                )
                .and(
                        IncidentSpecifications.hasImpact(
                                criteria.impact()
                        )
                )
                .and(
                        IncidentSpecifications.hasUrgency(
                                criteria.urgency()
                        )
                )
                .and(
                        IncidentSpecifications.hasTeamId(
                                criteria.teamId()
                        )
                )
                .and(
                        IncidentSpecifications.hasAssigneeId(
                                criteria.assigneeId()
                        )
                )
                .and(
                        IncidentSpecifications.containsText(
                                criteria.text()
                        )
                );

        return incidentRepository.findAll(
                specification,
                pageable
        );
    }

    public Incident updateAssessment(
            UUID incidentId,
            Impact impact,
            Urgency urgency
    ) {
        Incident incident = findIncident(incidentId);

        incident.updateAssessment(
                impact,
                urgency
        );

        return incident;
    }

    public Incident assignToTeam(
            UUID incidentId,
            String teamId
    ) {
        Incident incident = findIncident(incidentId);

        incident.assignToTeam(teamId);

        return incident;
    }

    public Incident assignToUser(
            UUID incidentId,
            String assigneeId
    ) {
        Incident incident = findIncident(incidentId);

        incident.assignToUser(assigneeId);

        return incident;
    }

    public Incident clearAssignee(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        incident.clearAssignee();

        return incident;
    }

    public Incident clearTeam(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        incident.clearTeam();

        return incident;
    }

    public Incident startProgress(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        incident.startProgress();

        return incident;
    }

    public Incident returnToOpen(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        incident.returnToOpen();

        return incident;
    }

    public Incident resolve(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        incident.resolve();

        return incident;
    }

    public Incident reopen(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        incident.reopen();

        return incident;
    }

    public Incident close(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        incident.close();

        return incident;
    }

    private Incident findIncident(UUID incidentId) {
        return incidentRepository
                .findById(incidentId)
                .orElseThrow(
                        () -> new IncidentNotFoundException(
                                incidentId
                        )
                );
    }
}