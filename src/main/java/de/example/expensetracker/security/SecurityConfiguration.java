package de.example.expensetracker.security;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository registrations,
                                            ObjectMapper mapper) throws Exception {
        var logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri("{baseUrl}/logged-out");
        var apiRequests = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
        var entryPoints = new LinkedHashMap<RequestMatcher, AuthenticationEntryPoint>();
        entryPoints.put(apiRequests, (request, response, exception) ->
                problem(mapper, request, response, HttpStatus.UNAUTHORIZED, "Login required"));
        var entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        var requestCache = new HttpSessionRequestCache();
        // API requests must get 401, rather than becoming a post-login browser destination.
        requestCache.setRequestMatcher(request -> "GET".equals(request.getMethod())
                && !apiRequests.matches(request));
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/login", "/logged-out", "/oauth2/**", "/login/oauth2/**", "/error")
                        .permitAll().anyRequest().authenticated())
                .requestCache(cache -> cache.requestCache(requestCache))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, exception) -> {
                            var authentication = SecurityContextHolder.getContext().getAuthentication();
                            boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken;
                            var status = anonymous && apiRequests.matches(request)
                                    ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
                            problem(mapper, request, response, status,
                                    status == HttpStatus.UNAUTHORIZED ? "Login required" : "Access denied or invalid CSRF token");
                        }))
                .oauth2Login(login -> login.loginPage("/login").defaultSuccessUrl("/api/me", true)
                        .failureHandler((request, response, exception) ->
                                problem(mapper, request, response, HttpStatus.UNAUTHORIZED, "OpenID Connect login failed")))
                .logout(config -> config.logoutSuccessHandler(logout));
        return http.build();
    }

    private static void problem(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                                HttpStatus status, String detail) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
