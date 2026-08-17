package com.nexusops.servicecore.audit.application;

import com.nexusops.servicecore.audit.domain.AuditAction;
import com.nexusops.servicecore.audit.domain.AuditEntityType;
import com.nexusops.servicecore.audit.domain.AuditEntry;
import com.nexusops.servicecore.audit.repository.AuditRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTests {

    @Test
    void recordsAuditEntryWithCurrentActorAndTime() {
        AuditRepository repository =
                mock(AuditRepository.class);

        AuditActorProvider actorProvider =
                mock(AuditActorProvider.class);

        Instant now =
                Instant.parse(
                        "2026-08-17T12:00:00Z"
                );

        Clock clock =
                Clock.fixed(
                        now,
                        ZoneOffset.UTC
                );

        when(actorProvider.currentActorId())
                .thenReturn("user-123");

        when(repository.save(any(AuditEntry.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        AuditService service =
                new AuditService(
                        repository,
                        actorProvider,
                        clock
                );

        UUID entityId = UUID.randomUUID();

        AuditEntry entry = service.record(
                AuditAction.KNOWLEDGE_ARTICLE_CREATED,
                AuditEntityType.KNOWLEDGE_ARTICLE,
                entityId,
                null,
                "version=1"
        );

        assertEquals(
                "user-123",
                entry.getActorId()
        );

        assertEquals(
                entityId,
                entry.getEntityId()
        );

        assertEquals(
                now,
                entry.getOccurredAt()
        );

        verify(repository).save(
                any(AuditEntry.class)
        );
    }
}