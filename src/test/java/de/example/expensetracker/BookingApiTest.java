package de.example.expensetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired javax.sql.DataSource dataSource;
    private static final String VALID = """
            {"type":"INCOME","description":"Gehalt","amount":"123456789.123456789","date":"2026-11-03",
             "owner_subject":"other"}
            """;

    @BeforeEach void clean() { jdbc.update("DELETE FROM expenses"); }

    private long create(String owner, String body) throws Exception {
        var response = mvc.perform(post("/api/bookings").with(oidcLogin().idToken(t -> t.subject(owner)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse();
        return mapper.readTree(response.getContentAsString()).get("id").asLong();
    }

    @Test void incomeCrudPreservesPrecisionAndLegacyApiCannotAccessIncome() throws Exception {
        long id = create("a", VALID);
        mvc.perform(get("/api/bookings/" + id).with(oidcLogin().idToken(t -> t.subject("a"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("123456789.123456789"))
                .andExpect(jsonPath("$.owner_subject").doesNotExist());
        for (String query : new String[]{"", "?from=2026-11-03", "?to=2026-11-03", "?from=2026-11-03&to=2026-11-03"}) {
            mvc.perform(get("/api/expenses" + query).with(oidcLogin().idToken(t -> t.subject("a"))))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        mvc.perform(get("/api/expenses/" + id).with(oidcLogin().idToken(t -> t.subject("a"))))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/expenses/" + id).with(oidcLogin().idToken(t -> t.subject("a")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"description":"test","amount":1,"date":"2026-11-03"}
                        """)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/expenses/" + id).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/bookings/" + id).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("INCOME", "EXPENSE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("EXPENSE"));
        mvc.perform(get("/api/expenses/" + id).with(oidcLogin().idToken(t -> t.subject("a"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").isNumber());
        mvc.perform(delete("/api/bookings/" + id).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/bookings/" + id).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?from=2026-11-03", "?to=2026-11-03", "?from=2026-11-03&to=2026-11-03"})
    void ownerIsolationAndInclusiveFilters(String query) throws Exception {
        long own = create("a", VALID);
        long foreign = create("b", VALID);
        jdbc.update("INSERT INTO expenses(description, amount, date) VALUES ('Legacy', 1, '2026-11-03')");
        long legacy = jdbc.queryForObject("SELECT id FROM expenses WHERE owner_subject IS NULL", Long.class);
        mvc.perform(get("/api/bookings/" + legacy).with(oidcLogin().idToken(t -> t.subject("a"))))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/bookings/" + legacy).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/bookings/" + legacy).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf()))
                .andExpect(status().isNotFound());
        create("a", VALID.replace("2026-11-03", "2026-12-03"));
        var response = mvc.perform(get("/api/bookings" + query).with(oidcLogin().idToken(t -> t.subject("a"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(own)).andReturn().getResponse();
        var list = mapper.readTree(response.getContentAsString());
        assertThat(list.size()).isEqualTo(query.isEmpty() || query.startsWith("?from=2026-11-03") && !query.contains("to=") ? 2 : 1);
        mvc.perform(get("/api/bookings/" + foreign).with(oidcLogin().idToken(t -> t.subject("a"))))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/bookings/" + foreign).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/bookings/" + foreign).with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"0\"", "\"-1\"", "\"abc\"", "null", "12.34", "\"1e3\""})
    void invalidAmountsAreProblemDetails(String amount) throws Exception {
        mvc.perform(post("/api/bookings").with(oidcLogin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"123456789.123456789\"", amount)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test void authenticationCsrfValidationAndUnknownIds() throws Exception {
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").with(oidcLogin()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings").with(oidcLogin()).with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings").with(oidcLogin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("Gehalt", " "))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        mvc.perform(post("/api/bookings").with(oidcLogin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("INCOME", "UNKNOWN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookings?from=2026-12-01&to=2026-11-01").with(oidcLogin()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookings?from=invalid").with(oidcLogin())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/bookings/999999").with(oidcLogin()).with(csrf())).andExpect(status().isNotFound());
    }
    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void concurrentDeletionReturnsNotFoundForWaitingWrite(String method) throws Exception {
        long id = create("a", VALID);
        String path = "/api/bookings/" + id;
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int blockerPid;
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT pg_backend_pid()")) {
                    result.next();
                    blockerPid = result.getInt(1);
                }
                try (var statement = connection.prepareStatement("DELETE FROM expenses WHERE id = ?")) {
                    statement.setLong(1, id);
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }

                var request = method.equals("PUT")
                        ? put(path).contentType(MediaType.APPLICATION_JSON).content(VALID.replace("Lunch", "Dinner"))
                        : delete(path);
                var response = executor.submit(() -> mvc.perform(request.with(oidcLogin().idToken(t -> t.subject("a"))).with(csrf()))
                        .andExpect(status().isNotFound()).andReturn());

                // Commit only once PostgreSQL confirms that the request is waiting on our deletion.
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                boolean blocked = false;
                while (System.nanoTime() < deadline) {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS (
                                SELECT 1 FROM pg_stat_activity
                                WHERE ? = ANY(pg_blocking_pids(pid))
                            )
                            """, Boolean.class, blockerPid));
                    if (blocked || response.isDone()) {
                        break;
                    }
                    Thread.sleep(20);
                }
                assertThat(blocked).as("%s must wait for the uncommitted deletion", method).isTrue();
                connection.commit();

                assertThat(response.get(10, java.util.concurrent.TimeUnit.SECONDS).getResponse().getContentAsString())
                        .contains("Booking " + id + " not found");
                mvc.perform(get(path).with(oidcLogin().idToken(t -> t.subject("a")))).andExpect(status().isNotFound());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
            } finally {
                connection.rollback();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

}
