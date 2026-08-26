package com.nexusops.servicecore.incident.repository;

import com.nexusops.servicecore.incident.domain.Incident;
import com.nexusops.servicecore.incident.domain.IncidentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository
        extends JpaRepository<Incident, UUID>,
        JpaSpecificationExecutor<Incident> {

    Optional<Incident> findBySourceAndSourceAlertId(
            IncidentSource source,
            UUID sourceAlertId
    );

    long countBySourceAndSourceAlertId(
            IncidentSource source,
            UUID sourceAlertId
    );
}
