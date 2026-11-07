package de.example.expensetracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrations() {
        var registration = ClientRegistration
                .withRegistrationId("keycloak")
                .clientId("expense-tracker-web").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .authorizationUri("http://localhost:8081/realms/expense-tracker/protocol/openid-connect/auth")
                .tokenUri("http://localhost:8081/realms/expense-tracker/protocol/openid-connect/token")
                .jwkSetUri("http://localhost:8081/realms/expense-tracker/protocol/openid-connect/certs")
                .issuerUri("http://localhost:8081/realms/expense-tracker")
                .userNameAttributeName("sub")
                .providerConfigurationMetadata(Map.of("end_session_endpoint",
                        "http://localhost:8081/realms/expense-tracker/protocol/openid-connect/logout"))
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17.11");
    }
}
