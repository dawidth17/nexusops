package com.nexusops.servicecore.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PostgresTestcontainersIntegrationTests {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usesContainerizedPostgresWithFlywaySchema() {
        String datasourceUrl =
                environment.getProperty(
                        "spring.datasource.url"
                );

        assertNotNull(datasourceUrl);

        assertTrue(
                datasourceUrl.startsWith(
                        "jdbc:tc:postgresql:18.4:"
                )
        );

        String databaseVersion =
                jdbcTemplate.queryForObject(
                        "SELECT version()",
                        String.class
                );

        assertNotNull(databaseVersion);

        assertTrue(
                databaseVersion.startsWith(
                        "PostgreSQL 18.4"
                )
        );

        Integer successfulMigrations =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE success = TRUE
                          AND version IS NOT NULL
                        """,
                        Integer.class
                );

        assertNotNull(successfulMigrations);

        assertEquals(
                10,
                successfulMigrations
        );

        assertTableExists("incidents");
        assertTableExists("incident_comments");
        assertTableExists("sla_policies");
        assertTableExists("incident_slas");
        assertTableExists("assets");
        assertTableExists("asset_assignments");
        assertTableExists("knowledge_articles");
        assertTableExists("knowledge_article_versions");
        assertTableExists("audit_entries");
        assertTableExists("processed_events");

        assertIndexExists(
                "uq_incidents_monitoring_source_alert"
        );

        assertIndexExists(
                "ix_processed_events_processed_at"
        );
    }

    private void assertTableExists(
            String tableName
    ) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                        Integer.class,
                        tableName
                );

        assertEquals(
                1,
                count
        );
    }

    private void assertIndexExists(
            String indexName
    ) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = ?
                        """,
                        Integer.class,
                        indexName
                );

        assertEquals(
                1,
                count
        );
    }
}
