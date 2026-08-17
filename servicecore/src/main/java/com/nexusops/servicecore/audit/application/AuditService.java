package com.nexusops.servicecore.audit.application;

import com.nexusops.servicecore.audit.domain.AuditAction;
import com.nexusops.servicecore.audit.domain.AuditEntityType;
import com.nexusops.servicecore.audit.domain.AuditEntry;
import com.nexusops.servicecore.audit.repository.AuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional
public class AuditService {

    private final AuditRepository auditRepository;
    private final AuditActorProvider actorProvider;
    private final Clock clock;

    public AuditService(
            AuditRepository auditRepository,
            AuditActorProvider actorProvider,
            Clock clock
    ) {
        this.auditRepository = auditRepository;
        this.actorProvider = actorProvider;
        this.clock = clock;
    }

    public AuditEntry record(
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            String beforeSummary,
            String afterSummary
    ) {
        return record(
                action,
                entityType,
                entityId,
                beforeSummary,
                afterSummary,
                null
        );
    }

    public AuditEntry record(
            AuditAction action,
            AuditEntityType entityType,
            UUID entityId,
            String beforeSummary,
            String afterSummary,
            UUID correlationId
    ) {
        AuditEntry entry = AuditEntry.create(
                actorProvider.currentActorId(),
                action,
                entityType,
                entityId,
                beforeSummary,
                afterSummary,
                clock.instant(),
                correlationId
        );

        return auditRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> getHistory(
            AuditEntityType entityType,
            UUID entityId,
            Pageable pageable
    ) {
        return auditRepository
                .findByEntityTypeAndEntityId(
                        entityType,
                        entityId,
                        pageable
                );
    }
}
