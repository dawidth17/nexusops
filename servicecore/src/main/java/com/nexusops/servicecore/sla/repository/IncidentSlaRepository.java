package com.nexusops.servicecore.sla.repository;

import com.nexusops.servicecore.sla.domain.IncidentSla;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IncidentSlaRepository
        extends JpaRepository<IncidentSla, UUID> {

    List<IncidentSla>
            findByFirstRespondedAtIsNullAndResponseBreachedAtIsNullAndFirstResponseDueAtBefore(
                    Instant now,
                    Pageable pageable
            );

    List<IncidentSla>
            findByResolvedAtIsNullAndResolutionBreachedAtIsNullAndResolutionDueAtBefore(
                    Instant now,
                    Pageable pageable
            );
}