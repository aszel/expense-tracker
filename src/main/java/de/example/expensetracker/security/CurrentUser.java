package de.example.expensetracker.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public OidcUser principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)
                || user.getSubject() == null || user.getSubject().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "An OpenID Connect login is required");
        }
        return user;
    }

    public String subject() {
        return principal().getSubject();
    }
}
