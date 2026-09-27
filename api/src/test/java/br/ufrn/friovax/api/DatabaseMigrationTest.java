package br.ufrn.friovax.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DatabaseMigrationTest {

    @Inject
    DataSource dataSource;

    @Inject
    Flyway flyway;

    @Test
    void shouldConnectToPostgres() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2));
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    void shouldApplyAllMigrationsAtStartup() {
        assertEquals(0, flyway.info().pending().length);
    }
}
