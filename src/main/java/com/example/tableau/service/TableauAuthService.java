package com.example.tableau.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TableauAuthService {
    private static final Logger log = LoggerFactory.getLogger(TableauAuthService.class);

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${tableau.api-version:3.17}")
    private String apiVersion;

    @Value("${tableau.base-url}")
    private String baseUrl;

    @Value("${tableau.auth-mode:PAT}")
    private String authMode;

    @Value("${tableau.pat.name:}")
    private String patName;

    @Value("${tableau.pat.secret:}")
    private String patSecret;

    @Value("${tableau.username:}")
    private String userName;

    @Value("${tableau.password:}")
    private String password;

    private final AtomicReference<String> token = new AtomicReference<>();
    private Instant tokenExpiry = Instant.EPOCH;

    public TableauAuthService(WebClient.Builder webClientBuilder, @Value("${tableau.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public synchronized Mono<String> getAuthToken() {
        if (token.get() != null && Instant.now().isBefore(tokenExpiry.minusSeconds(30))) {
            return Mono.just(token.get());
        }
        return signIn().map(t -> {
            token.set(t);
            // token expiry: keep simple - refresh every 50 minutes
            tokenExpiry = Instant.now().plusSeconds(50 * 60);
            return t;
        });
    }

    private Mono<String> signIn() {
        String signinUrl = "/api/" + apiVersion + "/auth/signin";
        try {
            if ("PAT".equalsIgnoreCase(authMode)) {
                var payload = Map.of("credentials", Map.of(
                        "personalAccessTokenName", patName,
                        "personalAccessTokenSecret", patSecret,
                        "site", Map.of("contentUrl", "")
                ));
                return webClient.post()
                        .uri(signinUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(mapper.writeValueAsString(payload))
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(this::extractTokenFromSigninResponse);
            } else {
                var payload = Map.of("credentials", Map.of(
                        "name", userName,
                        "password", password,
                        "site", Map.of("contentUrl", "")
                ));
                return webClient.post()
                        .uri(signinUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(mapper.writeValueAsString(payload))
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(this::extractTokenFromSigninResponse);
            }
        } catch (Exception e) {
            log.error("Error while signing in to Tableau: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    private String extractTokenFromSigninResponse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode credentials = root.path("credentials");
            if (!credentials.isMissingNode()) {
                String tokenVal = credentials.path("token").asText(null);
                if (tokenVal != null) return tokenVal;
            }
        } catch (Exception e) {
            log.warn("Failed to parse signin response: {}", e.getMessage());
        }
        throw new IllegalStateException("Could not obtain Tableau auth token from signin response");
    }
}