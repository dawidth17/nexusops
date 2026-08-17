package com.nexusops.servicecore.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationFromZeroTests {

    private static final String JDBC_URL =
            "jdbc:tc:postgresql:18.4:///flyway_zero"
                    + "?TC_DAEMON=true";

    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";

    @Test
    void createsCompleteSchemaFromEmptyPostgres()
            throws SQLException {

        Flyway flyway = Flyway.configure()
                .dataSource(
                        JDBC_URL,
                        USERNAME,
                        PASSWORD
                )
                .locations(
                        "classpath:db/migration"
                )
                .validateMigrationNaming(true)
                .load();

        flyway.migrate();
        flyway.validate();

        try (
                Connection connection =
                        DriverManager.getConnection(
                                JDBC_URL,
                                USERNAME,
                                PASSWORD
                        )
        ) {
            assertMigrationsApplied(connection);
            assertCoreTablesExist(connection);
            assertImportantIndexesExist(connection);
        }
    }

    private void assertMigrationsApplied(
            Connection connection
    ) throws SQLException {

        try (
                Statement statement =
                        connection.createStatement();

                ResultSet resultSet =
                        statement.executeQuery(
                                """
                                SELECT version
                                FROM flyway_schema_history
                                WHERE success = TRUE
                                  AND version IS NOT NULL
                                ORDER BY installed_rank
                                """
                        )
        ) {
            Set<String> versions =
                    new LinkedHashSet<>();

            while (resultSet.next()) {
                versions.add(
                        resultSet.getString("version")
                );
            }

            assertTrue(
                    versions.containsAll(
                            Set.of(
                                    "1",
                                    "2",
                                    "3",
                                    "4",
                                    "5",
                                    "6",
                                    "7",
                                    "8",
                                    "9"
                            )
                    )
            );

            assertEquals(
                    9,
                    versions.size()
            );
        }
    }

    private void assertCoreTablesExist(
            Connection connection
    ) throws SQLException {

        Set<String> expectedTables =
                Set.of(
                        "incidents",
                        "incident_comments",
                        "sla_policies",
                        "incident_slas",
                        "assets",
                        "asset_assignments",
                        "knowledge_articles",
                        "knowledge_article_versions",
                        "audit_entries"
                );

        try (
                Statement statement =
                        connection.createStatement();

                ResultSet resultSet =
                        statement.executeQuery(
                                """
                                SELECT table_name
                                FROM information_schema.tables
                                WHERE table_schema = 'public'
                                """
                        )
        ) {
            Set<String> actualTables =
                    new HashSet<>();

            while (resultSet.next()) {
                actualTables.add(
                        resultSet.getString(
                                "table_name"
                        )
                );
            }

            assertTrue(
                    actualTables.containsAll(
                            expectedTables
                    )
            );
        }
    }

    private void assertImportantIndexesExist(
            Connection connection
    ) throws SQLException {

        try (
                Statement statement =
                        connection.createStatement();

                ResultSet resultSet =
                        statement.executeQuery(
                                """
                                SELECT COUNT(*)
                                FROM pg_indexes
                                WHERE schemaname = 'public'
                                  AND indexname =
                                      'uq_asset_assignments_active_asset'
                                """
                        )
        ) {
            assertTrue(resultSet.next());

            assertEquals(
                    1,
                    resultSet.getInt(1)
            );
        }
    }
}