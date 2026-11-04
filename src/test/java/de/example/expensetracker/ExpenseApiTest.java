package de.example.expensetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseApiTest {

    private static final String VALID = """
            {"description":"Lunch","amount":12.34,"date":"2026-11-03"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

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
        mvc.perform(delete("/api/expenses/{id}", firstId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/expenses/{id}", secondId)).andExpect(status().isNoContent());
    }

    @Test
    void unknownIdsReturnProblemDetailsWithoutCreatingAnExpense() throws Exception {
        String path = "/api/expenses/9223372036854775807";
        expectProblem(get(path), 404).andExpect(jsonPath("$.detail").value(
                "Expense 9223372036854775807 not found"));
        expectProblem(put(path).contentType(MediaType.APPLICATION_JSON).content(VALID), 404);
        expectProblem(delete(path), 404);
        expectProblem(get(path), 404);
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
