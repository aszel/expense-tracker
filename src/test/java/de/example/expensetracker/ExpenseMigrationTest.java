package de.example.expensetracker;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(PostgresTestConfiguration.class)
@SpringBootTest
class ExpenseMigrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upgradePreservesExistingExpensesWithoutAssigningAnOwner() throws Exception {
        jdbc.execute("CREATE SCHEMA migration_test");
        try {
            migrate("classpath:db/changelog/001-create-expenses.sql");
            jdbc.update("INSERT INTO migration_test.expenses(description, amount, date) VALUES ('Legacy', 12.34567, '2026-11-03')");
            migrate("classpath:db/changelog/db.changelog-master.yaml");
            var legacy = jdbc.queryForMap("SELECT * FROM migration_test.expenses");
            assertThat(legacy.get("description")).isEqualTo("Legacy");
            assertThat(legacy.get("amount")).isEqualTo(new java.math.BigDecimal("12.34567"));
            assertThat(legacy.get("owner_subject")).isNull();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_indexes WHERE schemaname = 'migration_test'
                    AND indexname = 'expenses_owner_subject_id_idx'
                    """, Long.class)).isEqualTo(1);
        } finally {
            // This schema exists only in the disposable Testcontainers database.
            jdbc.execute("DROP SCHEMA migration_test CASCADE");
        }
    }

    private void migrate(String changelog) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setDefaultSchema("migration_test");
        liquibase.setLiquibaseSchema("migration_test");
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }
}
