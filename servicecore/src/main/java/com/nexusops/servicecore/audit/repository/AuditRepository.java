package com.nexusops.servicecore.audit.repository;

import com.nexusops.servicecore.audit.domain.AuditEntityType;
import com.nexusops.servicecore.audit.domain.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditRepository
        extends JpaRepository<AuditEntry, UUID> {

    Page<AuditEntry> findByEntityTypeAndEntityId(
            AuditEntityType entityType,
            UUID entityId,
            Pageable pageable
    );
}
