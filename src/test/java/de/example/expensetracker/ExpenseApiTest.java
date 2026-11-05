package de.example.expensetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.context.annotation.Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ExpenseApiTest {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM expenses");
    }

    private static final String VALID = """
            {"description":"Lunch","amount":12.34,"date":"2026-11-03"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void completeCrudLifecycle() throws Exception {
        var result = mvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.description").value("Lunch"))
                .andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.date").value("2026-11-03"))
                .andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        String path = "/api/expenses/" + id;
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("http://localhost" + path);

        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().json(result.getResponse().getContentAsString(), JsonCompareMode.STRICT));
        mvc.perform(get("/api/expenses")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));

        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("""
                        {"description":"Dinner","amount":25.75,"date":"2026-11-04"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.description").value("Dinner"))
                .andExpect(jsonPath("$.amount").value(25.75))
                .andExpect(jsonPath("$.date").value("2026-11-04"));
        mvc.perform(get(path)).andExpect(jsonPath("$.description").value("Dinner"));
        mvc.perform(delete(path)).andExpect(status().isNoContent()).andExpect(content().string(""));
        expectProblem(get(path), 404);
        mvc.perform(get("/api/expenses")).andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @Test
    void idsAreGeneratedAndAmountsRetainDecimalPrecision() throws Exception {
        String body = """
                {"id":999999,"description":"Precise","amount":123456789.123456789,"date":"2026-11-03"}
                """;
        var first = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var second = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long firstId = mapper.readTree(first).get("id").asLong();
        long secondId = mapper.readTree(second).get("id").asLong();
        assertThat(firstId).isPositive().isNotEqualTo(999999).isNotEqualTo(secondId);
        assertThat(first).contains("123456789.123456789");
        mvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[1].id").value(secondId));
        mvc.perform(delete("/api/expenses/{id}", firstId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/expenses/{id}", secondId)).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @CsvSource({
            ",,later|earlier|middle",
            "2026-11-03,,later|middle",
            ",2026-11-03,earlier|middle",
            "2026-11-02,2026-11-03,earlier|middle",
            "2026-11-03,2026-11-03,middle",
            "2026-12-01,,",
            ",2026-10-01,",
            "2026-11-05,2026-11-06,"
    })
    void dateFiltersIncludeBoundariesAndKeepIdOrder(String from, String to, String expected) throws Exception {
        String[] descriptions = {"later", "earlier", "middle"};
        String[] dates = {"2026-11-04", "2026-11-02", "2026-11-03"};
        for (int i = 0; i < dates.length; i++) {
            mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                            .content(VALID.replace("Lunch", descriptions[i]).replace("2026-11-03", dates[i])))
                    .andExpect(status().isCreated());
        }
        var request = get("/api/expenses");
        if (from != null) {
            request.param("from", from);
        }
        if (to != null) {
            request.param("to", to);
        }
        String response = mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var actual = mapper.readTree(response).findValuesAsText("description");
        assertThat(actual).containsExactly(expected == null ? new String[0] : expected.split("\\|"));
    }

    @Test
    void reversedDateRangeReturnsProblemDetails() throws Exception {
        expectProblem(get("/api/expenses").param("from", "2026-11-04").param("to", "2026-11-03"), 400);
    }

    @ParameterizedTest
    @CsvSource({
            "from,not-a-date", "to,not-a-date",
            "from,03.11.2026", "to,03.11.2026",
            "from,2026-02-30", "to,2026-02-30"
    })
    void invalidDateFiltersReturnProblemDetails(String parameter, String value) throws Exception {
        expectProblem(get("/api/expenses").param(parameter, value), 400);
    }

    @Test
    void unknownIdsReturnProblemDetailsWithoutCreatingAnExpense() throws Exception {
        String path = "/api/expenses/9223372036854775807";
        expectProblem(get(path), 404).andExpect(jsonPath("$.detail").value(
                "Expense 9223372036854775807 not found"));
        expectProblem(put(path).contentType(MediaType.APPLICATION_JSON).content(VALID), 404);
        expectProblem(delete(path), 404);
        expectProblem(get(path), 404);
        mvc.perform(get("/api/expenses")).andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void concurrentDeletionReturnsNotFoundForWaitingWrite(String method) throws Exception {
        var created = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String path = "/api/expenses/" + id;
        var executor = Executors.newSingleThreadExecutor();

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
                var response = executor.submit(() -> expectProblem(request, 404).andReturn());

                // Commit only once PostgreSQL confirms that the request is waiting on our deletion.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
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

                assertThat(response.get(10, TimeUnit.SECONDS).getResponse().getContentAsString())
                        .contains("Expense " + id + " not found");
                expectProblem(get(path), 404);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isZero();
            } finally {
                connection.rollback();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"description\":\" \",\"amount\":1,\"date\":\"2026-11-03\"}",
            "{\"description\":\"Lunch\",\"amount\":0,\"date\":\"2026-11-03\"}",
            "{\"description\":\"Lunch\",\"amount\":-1,\"date\":\"2026-11-03\"}",
            "{\"description\":\"Lunch\",\"amount\":null,\"date\":\"2026-11-03\"}",
            "{\"description\":null,\"amount\":1,\"date\":\"2026-11-03\"}",
            "{\"description\":\"Lunch\",\"amount\":1,\"date\":null}"
    })
    void invalidFieldsAreRejectedOnCreateAndUpdate(String body) throws Exception {
        var result = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andReturn();
        String path = "/api/expenses/" + mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        expectProblem(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body), 400)
                .andExpect(jsonPath("$.errors[0].field").isString())
                .andExpect(jsonPath("$.errors[0].message").isString());
        expectProblem(put(path).contentType(MediaType.APPLICATION_JSON).content(body), 400);
        mvc.perform(get(path)).andExpect(content().json(result.getResponse().getContentAsString(), JsonCompareMode.STRICT));
        mvc.perform(delete(path)).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{",
            "",
            "{\"description\":\"Lunch\",\"amount\":\"abc\",\"date\":\"2026-11-03\"}",
            "{\"description\":\"Lunch\",\"amount\":1,\"date\":\"2026-02-30\"}"
    })
    void unreadableRequestsReturnProblemDetails(String body) throws Exception {
        expectProblem(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body), 400);
        expectProblem(put("/api/expenses/1").contentType(MediaType.APPLICATION_JSON).content(body), 400);
    }

    @Test
    void descriptionContainingNullCharacterReturnsProblemDetailsOnCreate() throws Exception {
        String body = """
                {"description":"Lunch\\u0000break","amount":12.34,"date":"2026-11-03"}
                """;
        assertThat(mapper.readTree(body).get("description").asText()).contains("\0");

        expectProblem(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body), 400);
    }

    @Test
    void invalidPathIdReturnsProblemDetails() throws Exception {
        expectProblem(get("/api/expenses/not-a-number"), 400);
    }

    private ResultActions expectProblem(MockHttpServletRequestBuilder request, int code) throws Exception {
        return mvc.perform(request).andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.instance").isString());
    }
}
