package com.nexusops.servicecore.incident.application;

import com.nexusops.servicecore.incident.domain.Impact;
import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.Urgency;
import com.nexusops.servicecore.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
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

    public Incident updateAssessment(
            UUID incidentId,
            Impact impact,
            Urgency urgency
    ) {
        Incident incident = findIncident(incidentId);

        incident.updateAssessment(impact, urgency);

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
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }
}