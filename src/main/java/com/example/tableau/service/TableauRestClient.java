package com.example.tableau.service;

import com.example.tableau.config.TableauApiConfig;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Service for making REST API requests to Tableau Server.
 * Used for administrative tasks like authentication, site management, and server info.
 */
@Service
public class TableauRestClient {
    private static final Logger log = LoggerFactory.getLogger(TableauRestClient.class);

    private final WebClient webClient;
    private final TableauAuthService authService;
    private final TableauApiConfig apiConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    public TableauRestClient(WebClient.Builder webClientBuilder,
                             TableauAuthService authService,
                             TableauApiConfig apiConfig) {
        this.webClient = webClientBuilder.baseUrl(apiConfig.getBaseUrl()).build();
        this.authService = authService;
        this.apiConfig = apiConfig;
    }

    /**
     * Get server info (no authentication required).
     */
    public Mono<JsonNode> getServerInfo() {
        return webClient.get()
                .uri("/api/" + apiConfig.getApiVersion() + "/serverinfo")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(this::isRetryableError))
                .doOnSuccess(info -> log.info("Retrieved server info"))
                .doOnError(e -> log.error("Failed to get server info: {}", e.getMessage()));
    }

    /**
     * Get all sites the authenticated user has access to.
     */
    public Mono<List<JsonNode>> getSites() {
        return authService.getAuthToken()
                .flatMap(token -> webClient.get()
                        .uri("/api/" + apiConfig.getApiVersion() + "/sites")
                        .header("X-Tableau-Auth", token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(json -> {
                            JsonNode root = parseResponse(json);
                            JsonNode sites = root.path("sites").path("site");
                            List<JsonNode> result = new ArrayList<>();
                            if (sites.isArray()) {
                                sites.forEach(result::add);
                            }
                            return result;
                        })
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                .filter(this::isRetryableError))
                        .doOnSuccess(sites -> log.info("Retrieved {} sites", sites.size()))
                        .doOnError(e -> log.error("Failed to get sites: {}", e.getMessage())));
    }

    /**
     * Get current site details.
     */
    public Mono<JsonNode> getCurrentSite() {
        String siteId = authService.getCurrentSiteId();
        if (siteId == null || siteId.isEmpty()) {
            return Mono.error(new TableauApiException("No active site. Please sign in first."));
        }
        
        return authService.getAuthToken()
                .flatMap(token -> webClient.get()
                        .uri("/api/" + apiConfig.getApiVersion() + "/sites/" + siteId)
                        .header("X-Tableau-Auth", token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(json -> parseResponse(json).path("site"))
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                .filter(this::isRetryableError)));
    }

    /**
     * Get projects for the current site with owner information.
     */
    public Mono<List<JsonNode>> getProjects() {
        return getPagedResourceWithFields("/sites/{siteId}/projects", "projects", "project", "_all_");
    }
    
    /**
     * Helper method to get paged resources with fields parameter.
     */
    private Mono<List<JsonNode>> getPagedResourceWithFields(String pathTemplate, String containerKey, String itemKey, String fields) {
        String siteId = authService.getCurrentSiteId();
        if (siteId == null || siteId.isEmpty()) {
            return Mono.error(new TableauApiException("No active site. Please sign in first."));
        }
        
        String path = pathTemplate.replace("{siteId}", siteId);
        List<JsonNode> allResults = new ArrayList<>();
        
        return fetchAllPagesWithFields(path, containerKey, itemKey, 1, 100, fields, allResults);
    }
    
    private Mono<List<JsonNode>> fetchAllPagesWithFields(String path, String containerKey, String itemKey,
                                                          int pageNumber, int pageSize, String fields, List<JsonNode> results) {
        return authService.getAuthToken()
                .flatMap(token -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/" + apiConfig.getApiVersion() + path)
                                .queryParam("pageNumber", pageNumber)
                                .queryParam("pageSize", pageSize)
                                .queryParam("fields", fields)
                                .build())
                        .header("X-Tableau-Auth", token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(json -> {
                            JsonNode root = parseResponse(json);
                            JsonNode container = root.path(containerKey);
                            JsonNode items = container.path(itemKey);
                            
                            if (items.isArray()) {
                                items.forEach(results::add);
                            }
                            
                            // Check pagination
                            JsonNode pagination = root.path("pagination");
                            int totalAvailable = pagination.path("totalAvailable").asInt(0);
                            int pageNum = pagination.path("pageNumber").asInt(1);
                            int pageSz = pagination.path("pageSize").asInt(100);
                            
                            int fetchedSoFar = pageNum * pageSz;
                            if (fetchedSoFar < totalAvailable) {
                                return fetchAllPagesWithFields(path, containerKey, itemKey, pageNumber + 1, pageSize, fields, results);
                            }
                            
                            return Mono.just(results);
                        })
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                .filter(this::isRetryableError)));
    }

    /**
     * Get workbooks for the current site.
     */
    public Mono<List<JsonNode>> getWorkbooks() {
        return getPagedResource("/sites/{siteId}/workbooks", "workbooks", "workbook");
    }

    /**
     * Get data sources for the current site.
     */
    public Mono<List<JsonNode>> getDataSources() {
        return getPagedResource("/sites/{siteId}/datasources", "datasources", "datasource");
    }

    /**
     * Get users for the current site.
     */
    public Mono<List<JsonNode>> getUsers() {
        return getPagedResource("/sites/{siteId}/users", "users", "user");
    }

    /**
     * Helper method to get paged resources.
     */
    private Mono<List<JsonNode>> getPagedResource(String pathTemplate, String containerKey, String itemKey) {
        String siteId = authService.getCurrentSiteId();
        if (siteId == null || siteId.isEmpty()) {
            return Mono.error(new TableauApiException("No active site. Please sign in first."));
        }
        
        String path = pathTemplate.replace("{siteId}", siteId);
        List<JsonNode> allResults = new ArrayList<>();
        
        return fetchAllPages(path, containerKey, itemKey, 1, 100, allResults);
    }

    private Mono<List<JsonNode>> fetchAllPages(String path, String containerKey, String itemKey,
                                                int pageNumber, int pageSize, List<JsonNode> results) {
        return authService.getAuthToken()
                .flatMap(token -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/" + apiConfig.getApiVersion() + path)
                                .queryParam("pageNumber", pageNumber)
                                .queryParam("pageSize", pageSize)
                                .build())
                        .header("X-Tableau-Auth", token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(json -> {
                            JsonNode root = parseResponse(json);
                            JsonNode container = root.path(containerKey);
                            JsonNode items = container.path(itemKey);
                            
                            if (items.isArray()) {
                                items.forEach(results::add);
                            }
                            
                            // Check pagination
                            JsonNode pagination = root.path("pagination");
                            int totalAvailable = pagination.path("totalAvailable").asInt(0);
                            int pageNum = pagination.path("pageNumber").asInt(1);
                            int pageSz = pagination.path("pageSize").asInt(100);
                            
                            int fetchedSoFar = pageNum * pageSz;
                            if (fetchedSoFar < totalAvailable) {
                                return fetchAllPages(path, containerKey, itemKey, pageNumber + 1, pageSize, results);
                            }
                            
                            return Mono.just(results);
                        })
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                .filter(this::isRetryableError)));
    }

    private JsonNode parseResponse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse REST API response: {}", e.getMessage());
            throw new TableauApiException("Failed to parse REST API response", e);
        }
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }
}
