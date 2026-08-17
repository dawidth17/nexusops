package com.nexusops.servicecore.incident.repository;

import com.nexusops.servicecore.incident.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface IncidentRepository
        extends JpaRepository<Incident, UUID>,
        JpaSpecificationExecutor<Incident> {
}
