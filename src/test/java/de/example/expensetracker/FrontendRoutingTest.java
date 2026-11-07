package de.example.expensetracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FrontendRoutingTest {
    @Autowired MockMvc mvc;
    @Test void uiRoutesRequireLoginAndServeTheBuiltApplication() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/login"));
        for (String path : new String[]{"/", "/bookings/new", "/bookings/1/edit"}) {
            mvc.perform(get(path).with(oidcLogin())).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(containsString("id=\"root\"")));
        }
        mvc.perform(get("/api/not-found").with(oidcLogin())).andExpect(status().isNotFound());
        mvc.perform(get("/unknown-page").with(oidcLogin())).andExpect(status().isNotFound());
        mvc.perform(get("/assets/missing.js").with(oidcLogin())).andExpect(status().isNotFound());
    }
    @Test void browserOidcFailureShowsALoginLinkWithoutRedirecting() throws Exception {
        mvc.perform(get("/login/oauth2/code/keycloak").param("code", "invalid").param("state", "invalid")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("href=\"/login\"")));
    }
}
