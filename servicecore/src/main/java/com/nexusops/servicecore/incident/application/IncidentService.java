package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentSource;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import com.nexusops.servicecore.incident.repository.IncidentSpecifications;
import com.nexusops.servicecore.sla.application.SlaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final SlaService slaService;
    private final Clock clock;

    public IncidentService(
            IncidentRepository incidentRepository,
            SlaService slaService,
            Clock clock
    ) {
        this.incidentRepository = incidentRepository;
        this.slaService = slaService;
        this.clock = clock;
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

        Incident savedIncident =
                incidentRepository.saveAndFlush(
                        incident
                );

        slaService.createForIncident(
                savedIncident
        );

        return savedIncident;
    }

    public Incident createFromMonitoring(
            String title,
            String description,
            Impact impact,
            Urgency urgency,
            UUID sourceAlertId,
            String correlationId
    ) {
        return incidentRepository
                .findBySourceAndSourceAlertId(
                        IncidentSource.MONITORING,
                        sourceAlertId
                )
                .orElseGet(
                        () -> createMonitoringIncident(
                                title,
                                description,
                                impact,
                                urgency,
                                sourceAlertId,
                                correlationId
                        )
                );
    }

    public Incident recordMonitoringRecovery(
            UUID sourceAlertId,
            Instant recoveredAt,
            String recoveryMessage
    ) {
        Incident incident = incidentRepository
                .findBySourceAndSourceAlertId(
                        IncidentSource.MONITORING,
                        sourceAlertId
                )
                .orElseThrow(
                        () -> new IllegalStateException(
                                "monitoring incident not found for alert "
                                        + sourceAlertId
                        )
                );

        incident.recordMonitoringRecovery(
                recoveredAt,
                recoveryMessage
        );

        return incident;
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
        Incident incident = findIncident(
                incidentId
        );

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
        Incident incident = findIncident(
                incidentId
        );

        incident.assignToTeam(
                teamId
        );

        return incident;
    }

    public Incident assignToUser(
            UUID incidentId,
            String assigneeId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.assignToUser(
                assigneeId
        );

        return incident;
    }

    public Incident clearAssignee(
            UUID incidentId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.clearAssignee();

        return incident;
    }

    public Incident clearTeam(
            UUID incidentId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.clearTeam();

        return incident;
    }

    public Incident startProgress(
            UUID incidentId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.startProgress();

        slaService.markFirstResponse(
                incidentId,
                clock.instant()
        );

        return incident;
    }

    public Incident returnToOpen(
            UUID incidentId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.returnToOpen();

        return incident;
    }

    public Incident resolve(
            UUID incidentId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.resolve();

        slaService.markResolved(
                incidentId,
                clock.instant()
        );

        return incident;
    }

    public Incident reopen(
            UUID incidentId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.reopen();

        slaService.markReopened(
                incidentId
        );

        return incident;
    }

    public Incident close(
            UUID incidentId
    ) {
        Incident incident = findIncident(
                incidentId
        );

        incident.close();

        return incident;
    }

    private Incident createMonitoringIncident(
            String title,
            String description,
            Impact impact,
            Urgency urgency,
            UUID sourceAlertId,
            String correlationId
    ) {
        Incident incident = Incident.createFromMonitoring(
                title,
                description,
                impact,
                urgency,
                sourceAlertId,
                correlationId
        );

        Incident savedIncident =
                incidentRepository.saveAndFlush(
                        incident
                );

        slaService.createForIncident(
                savedIncident
        );

        return savedIncident;
    }

    private Incident findIncident(
            UUID incidentId
    ) {
        return incidentRepository
                .findById(
                        incidentId
                )
                .orElseThrow(
                        () -> new IncidentNotFoundException(
                                incidentId
                        )
                );
    }
}
