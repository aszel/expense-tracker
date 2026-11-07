package de.example.expensetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ClientRegistrationRepository registrations;

    private static final String VALID = """
            {"description":"Lunch","amount":12.345,"date":"2026-11-03","owner_subject":"user-b"}
            """;

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM expenses"); }

    @Test
    void anonymousApiRequestsReturnUnauthorizedAndHealthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        for (String path : List.of("/api/expenses", "/api/expenses/1", "/api/me", "/api/csrf")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.instance").value(path));
        }
        mvc.perform(post("/api/expenses").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRedirectsToConfiguredRealmAndMeUsesSubject() throws Exception {
        mvc.perform(get("/login")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
        mvc.perform(get("/oauth2/authorization/keycloak")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "http://localhost:8081/realms/expense-tracker/protocol/openid-connect/auth?")));
        mvc.perform(get("/api/me").with(oidcLogin().idToken(t -> t.subject("user-a")
                        .claim("preferred_username", "alice"))))
                .andExpect(status().isOk()).andExpect(content().json("""
                        {"id":"user-a","username":"alice"}
                        """));
    }

    @Test
    void invalidOidcCallbackReturnsProblemDetailsInsteadOfALoginLoop() throws Exception {
        mvc.perform(get("/login/oauth2/code/keycloak").param("code", "invalid").param("state", "invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("OpenID Connect login failed"));
    }

    @Test
    void allDateFilterVariantsAndDirectAccessAreIsolated() throws Exception {
        long own = create("user-a");
        long foreign = create("user-b");
        long orphan = jdbc.queryForObject("""
                INSERT INTO expenses(description, amount, date) VALUES ('Legacy', 1, '2026-11-03') RETURNING id
                """, Long.class);
        assertThat(jdbc.queryForObject("SELECT owner_subject FROM expenses WHERE id = ?", String.class, own))
                .isEqualTo("user-a");
        for (String query : List.of("", "?from=2026-11-03", "?to=2026-11-03", "?from=2026-11-03&to=2026-11-03")) {
            mvc.perform(get("/api/expenses" + query).with(oidcLogin().idToken(t -> t.subject("user-a"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(own)).andExpect(jsonPath("$[0].owner_subject").doesNotExist());
        }
        for (long id : List.of(foreign, orphan, Long.MAX_VALUE)) {
            String path = "/api/expenses/" + id;
            mvc.perform(get(path).with(oidcLogin().idToken(t -> t.subject("user-a"))))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Expense " + id + " not found"));
            mvc.perform(put(path).with(oidcLogin().idToken(t -> t.subject("user-a"))).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(path).with(oidcLogin().idToken(t -> t.subject("user-a"))).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/expenses/" + own).with(oidcLogin().idToken(t -> t.subject("user-a"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT owner_subject FROM expenses WHERE id = ?", String.class, own))
                .isEqualTo("user-a");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses", Long.class)).isEqualTo(3);
        mvc.perform(get("/api/expenses").with(oidcLogin().idToken(t -> t.subject("user-b"))))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(foreign));
    }

    @Test
    void writesAndLogoutRequireValidCsrf() throws Exception {
        for (var request : List.of(post("/api/expenses"), put("/api/expenses/1"), delete("/api/expenses/1"), post("/logout"))) {
            mvc.perform(request.with(oidcLogin()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(post("/api/expenses").with(oidcLogin()).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void realCsrfEndpointTokenWorksAndLogoutInvalidatesSession() throws Exception {
        var registration = registrations.findByRegistrationId("keycloak");
        var token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "user-a", "preferred_username", "alice", "iss", registration.getProviderDetails().getIssuerUri()));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token);
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
        var csrf = mapper.readTree(mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/expenses").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isCreated());
        mvc.perform(post("/logout").session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("http://localhost:8081/realms/expense-tracker/protocol/openid-connect/logout?"),
                        org.hamcrest.Matchers.containsString("id_token_hint=test-id-token"),
                        org.hamcrest.Matchers.containsString("post_logout_redirect_uri="))));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/expenses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/logged-out")).andExpect(status().isOk());
    }

    private long create(String subject) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/expenses").with(oidcLogin().idToken(t -> t.subject(subject)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
