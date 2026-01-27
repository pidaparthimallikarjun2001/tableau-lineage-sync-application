package com.example.tableau.service;

import com.example.tableau.config.TableauApiConfig;
import com.example.tableau.dto.SiteSwitchResponse;
import com.example.tableau.exception.TableauApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for handling Tableau authentication and site switching.
 * Manages authentication tokens and site context for API calls.
 */
@Service
public class TableauAuthService {
    private static final Logger log = LoggerFactory.getLogger(TableauAuthService.class);

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TableauApiConfig apiConfig;

    private final AtomicReference<String> token = new AtomicReference<>();
    private final AtomicReference<String> currentSiteId = new AtomicReference<>();
    private final AtomicReference<String> currentSiteName = new AtomicReference<>();
    private final AtomicReference<String> currentSiteContentUrl = new AtomicReference<>();
    private Instant tokenExpiry = Instant.EPOCH;

    public TableauAuthService(WebClient.Builder webClientBuilder, TableauApiConfig apiConfig) {
        this.apiConfig = apiConfig;
        this.webClient = webClientBuilder.baseUrl(apiConfig.getBaseUrl()).build();
    }

    /**
     * Get a valid authentication token, refreshing if necessary.
     */
    public synchronized Mono<String> getAuthToken() {
        if (token.get() != null && Instant.now().isBefore(tokenExpiry.minusSeconds(30))) {
            return Mono.just(token.get());
        }
        // signIn() now updates internal state, so we just need to extract the token
        return signIn(currentSiteContentUrl.get()).map(SiteSwitchResponse::getAuthToken);
    }

    /**
     * Sign in to Tableau and return authentication details.
     */
    public Mono<SiteSwitchResponse> signIn(String siteContentUrl) {
        String signinUrl = "/api/" + apiConfig.getApiVersion() + "/auth/signin";
        String targetSite = normalizeSiteContentUrl(siteContentUrl);
        
        log.info("Signing in to Tableau at {} for site: {}", apiConfig.getBaseUrl(), 
                 targetSite.isEmpty() ? "<default>" : targetSite);
        
        try {
            Map<String, Object> payload;
            if ("PAT".equalsIgnoreCase(apiConfig.getAuthMode())) {
                payload = Map.of("credentials", Map.of(
                        "personalAccessTokenName", apiConfig.getPatName(),
                        "personalAccessTokenSecret", apiConfig.getPatSecret(),
                        "site", Map.of("contentUrl", targetSite)
                ));
            } else {
                payload = Map.of("credentials", Map.of(
                        "name", apiConfig.getUsername(),
                        "password", apiConfig.getPassword(),
                        "site", Map.of("contentUrl", targetSite)
                ));
            }
            
            return webClient.post()
                    .uri(signinUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(mapper.writeValueAsString(payload))
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::parseSignInResponse)
                    .doOnSuccess(response -> {
                        // Update internal authentication state
                        token.set(response.getAuthToken());
                        currentSiteId.set(response.getSiteId());
                        currentSiteName.set(response.getSiteName());
                        currentSiteContentUrl.set(response.getSiteContentUrl());
                        apiConfig.setCurrentAuthToken(response.getAuthToken());
                        apiConfig.setCurrentSiteId(response.getSiteId());
                        apiConfig.setCurrentSiteContentUrl(response.getSiteContentUrl());
                        tokenExpiry = Instant.now().plusSeconds(50 * 60);
                        log.info("Successfully signed in to site: {}", response.getSiteName());
                    })
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(this::isRetryableError)
                            .doBeforeRetry(signal -> log.warn("Retrying sign-in, attempt {}", signal.totalRetries() + 1)))
                    .doOnError(e -> log.error("Failed to sign in to Tableau: {}", e.getMessage()));
        } catch (Exception e) {
            log.error("Error preparing sign-in request: {}", e.getMessage(), e);
            return Mono.error(new TableauApiException("Failed to sign in to Tableau", e));
        }
    }

    /**
     * Switch to a different Tableau site.
     * Uses the REST API switchSite method.
     */
    public Mono<SiteSwitchResponse> switchSite(String siteContentUrl) {
        String normalizedSite = normalizeSiteContentUrl(siteContentUrl);
        log.info("Switching to site: {}", normalizedSite.isEmpty() ? "<default>" : normalizedSite);
        
        return getAuthToken()
                .flatMap(authToken -> {
                    String switchUrl = "/api/" + apiConfig.getApiVersion() + "/auth/switchSite";
                    try {
                        Map<String, Object> payload = Map.of("site", Map.of("contentUrl", normalizedSite));
                        
                        return webClient.post()
                                .uri(switchUrl)
                                .header("X-Tableau-Auth", authToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(mapper.writeValueAsString(payload))
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(this::parseSignInResponse)
                                .doOnSuccess(response -> {
                                    token.set(response.getAuthToken());
                                    currentSiteId.set(response.getSiteId());
                                    currentSiteName.set(response.getSiteName());
                                    currentSiteContentUrl.set(response.getSiteContentUrl());
                                    apiConfig.setCurrentAuthToken(response.getAuthToken());
                                    apiConfig.setCurrentSiteId(response.getSiteId());
                                    apiConfig.setCurrentSiteContentUrl(response.getSiteContentUrl());
                                    tokenExpiry = Instant.now().plusSeconds(50 * 60);
                                    log.info("Successfully switched to site: {}", response.getSiteName());
                                })
                                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                        .filter(this::isRetryableError));
                    } catch (Exception e) {
                        return Mono.error(new TableauApiException("Failed to switch site", e));
                    }
                });
    }

    /**
     * Sign out from Tableau.
     */
    public Mono<Void> signOut() {
        String currentToken = token.get();
        if (currentToken == null) {
            return Mono.empty();
        }
        
        String signoutUrl = "/api/" + apiConfig.getApiVersion() + "/auth/signout";
        
        return webClient.post()
                .uri(signoutUrl)
                .header("X-Tableau-Auth", currentToken)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> {
                    token.set(null);
                    currentSiteId.set(null);
                    currentSiteName.set(null);
                    currentSiteContentUrl.set(null);
                    apiConfig.clearSession();
                    tokenExpiry = Instant.EPOCH;
                    log.info("Successfully signed out from Tableau");
                })
                .onErrorResume(e -> {
                    log.warn("Error during sign out: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Get the current site ID.
     */
    public String getCurrentSiteId() {
        return currentSiteId.get();
    }

    /**
     * Get the current site name.
     */
    public String getCurrentSiteName() {
        return currentSiteName.get();
    }

    /**
     * Get the current site content URL.
     */
    public String getCurrentSiteContentUrl() {
        return currentSiteContentUrl.get();
    }

    private SiteSwitchResponse parseSignInResponse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode credentials = root.path("credentials");
            
            if (credentials.isMissingNode()) {
                throw new TableauApiException("Invalid sign-in response: missing credentials");
            }
            
            String tokenVal = credentials.path("token").asText(null);
            JsonNode site = credentials.path("site");
            String siteId = site.path("id").asText(null);
            String siteName = site.path("name").asText(null);
            String contentUrl = site.path("contentUrl").asText("");
            
            if (tokenVal == null) {
                throw new TableauApiException("Could not obtain auth token from response");
            }
            
            return SiteSwitchResponse.builder()
                    .success(true)
                    .authToken(tokenVal)
                    .siteId(siteId)
                    .siteName(siteName)
                    .siteContentUrl(contentUrl)
                    .message("Authentication successful")
                    .build();
        } catch (TableauApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse sign-in response: {}", e.getMessage());
            throw new TableauApiException("Failed to parse authentication response", e);
        }
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    /**
     * Normalize site content URL.
     * Maps "default" (case-insensitive) to empty string for Tableau API calls.
     * Also handles null values and uses configured default site if needed.
     */
    private String normalizeSiteContentUrl(String siteContentUrl) {
        // If explicitly provided and is "default" (case-insensitive), map to empty string
        if (siteContentUrl != null && "default".equalsIgnoreCase(siteContentUrl.trim())) {
            return "";
        }
        
        // If provided and not "default", use as-is
        if (siteContentUrl != null && !siteContentUrl.trim().isEmpty()) {
            return siteContentUrl.trim();
        }
        
        // If null or empty, use configured default site (which may also be empty for default site)
        String defaultSite = apiConfig.getDefaultSiteId();
        if (defaultSite != null && "default".equalsIgnoreCase(defaultSite.trim())) {
            return "";
        }
        
        return defaultSite != null ? defaultSite : "";
    }
}