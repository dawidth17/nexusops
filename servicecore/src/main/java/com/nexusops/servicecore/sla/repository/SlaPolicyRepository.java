package com.nexusops.servicecore.sla.repository;

import com.nexusops.servicecore.incident.domain.Priority;
import com.nexusops.servicecore.sla.domain.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository
        extends JpaRepository<SlaPolicy, Priority> {
}