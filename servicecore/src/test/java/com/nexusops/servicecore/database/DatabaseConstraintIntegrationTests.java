package com.nexusops.servicecore.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class DatabaseConstraintIntegrationTests {

    private static final String POSTGRES_RAISE_EXCEPTION_SQL_STATE =
            "P0001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsAssigneeForAvailableAsset() {
        UUID assetId = UUID.randomUUID();
        OffsetDateTime now = now();

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO assets (
                                    id,
                                    asset_tag,
                                    type,
                                    manufacturer,
                                    model,
                                    serial_number,
                                    status,
                                    assignee_id,
                                    created_at,
                                    updated_at
                                )
                                VALUES (
                                    ?,
                                    ?,
                                    'LAPTOP',
                                    'Dell',
                                    'Latitude Test',
                                    ?,
                                    'AVAILABLE',
                                    'user-123',
                                    ?,
                                    ?
                                )
                                """,
                                assetId,
                                "CONSTRAINT-ASSET-" + assetId,
                                "CONSTRAINT-SERIAL-" + assetId,
                                now,
                                now
                        )
        );
    }

    @Test
    void rejectsTwoActiveAssignmentsForSameAsset() {
        UUID assetId = UUID.randomUUID();
        OffsetDateTime now = now();

        jdbcTemplate.update(
                """
                INSERT INTO assets (
                    id,
                    asset_tag,
                    type,
                    manufacturer,
                    model,
                    serial_number,
                    status,
                    assignee_id,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    'LAPTOP',
                    'Dell',
                    'Latitude Test',
                    ?,
                    'ASSIGNED',
                    'user-123',
                    ?,
                    ?
                )
                """,
                assetId,
                "ACTIVE-ASSET-" + assetId,
                "ACTIVE-SERIAL-" + assetId,
                now,
                now
        );

        jdbcTemplate.update(
                """
                INSERT INTO asset_assignments (
                    id,
                    asset_id,
                    assignee_id,
                    assigned_at,
                    returned_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    NULL
                )
                """,
                UUID.randomUUID(),
                assetId,
                "user-123",
                now
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO asset_assignments (
                                    id,
                                    asset_id,
                                    assignee_id,
                                    assigned_at,
                                    returned_at
                                )
                                VALUES (
                                    ?,
                                    ?,
                                    ?,
                                    ?,
                                    NULL
                                )
                                """,
                                UUID.randomUUID(),
                                assetId,
                                "user-456",
                                now
                        )
        );
    }

    @Test
    void rejectsAuditEntryUpdates() {
        UUID auditId = createAuditEntry();

        assertAuditMutationRejected(
                () ->
                        jdbcTemplate.update(
                                """
                                UPDATE audit_entries
                                SET actor_id = 'changed-user'
                                WHERE id = ?
                                """,
                                auditId
                        )
        );
    }

    @Test
    void rejectsAuditEntryDeletes() {
        UUID auditId = createAuditEntry();

        assertAuditMutationRejected(
                () ->
                        jdbcTemplate.update(
                                """
                                DELETE FROM audit_entries
                                WHERE id = ?
                                """,
                                auditId
                        )
        );
    }

    private void assertAuditMutationRejected(
            Runnable operation
    ) {
        UncategorizedSQLException exception =
                assertThrows(
                        UncategorizedSQLException.class,
                        operation::run
                );

        SQLException sqlException =
                exception.getSQLException();

        assertNotNull(sqlException);

        assertEquals(
                POSTGRES_RAISE_EXCEPTION_SQL_STATE,
                sqlException.getSQLState()
        );
    }

    private UUID createAuditEntry() {
        UUID auditId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO audit_entries (
                    id,
                    actor_id,
                    action,
                    entity_type,
                    entity_id,
                    before_summary,
                    after_summary,
                    occurred_at,
                    correlation_id
                )
                VALUES (
                    ?,
                    'test-user',
                    'KNOWLEDGE_ARTICLE_CREATED',
                    'KNOWLEDGE_ARTICLE',
                    ?,
                    NULL,
                    'created',
                    ?,
                    NULL
                )
                """,
                auditId,
                UUID.randomUUID(),
                now()
        );

        return auditId;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(
                ZoneOffset.UTC
        );
    }
}