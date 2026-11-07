package de.example.expensetracker.security;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class AccountController {
    private final CurrentUser currentUser;

    public AccountController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/login")
    public RedirectView login() {
        return new RedirectView("/oauth2/authorization/keycloak");
    }

    @GetMapping("/api/me")
    public UserResponse me() {
        var user = currentUser.principal();
        return new UserResponse(user.getSubject(), user.getPreferredUsername());
    }

    @GetMapping("/api/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @GetMapping(value = "/logged-out", produces = MediaType.TEXT_HTML_VALUE)
    public String loggedOut() {
        return """
                <!doctype html><html lang="de"><meta charset="utf-8"><title>Abgemeldet</title>
                <body><h1>Du bist abgemeldet.</h1><a href="/login">Erneut anmelden</a></body></html>
                """;
    }

    public record UserResponse(String id, String username) {}
    public record CsrfResponse(String token, String headerName) {}
}
