package com.example.tableau.service;

import com.example.tableau.config.CollibraApiConfig;
import com.example.tableau.dto.collibra.CollibraAsset;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * REST client for communicating with Collibra API.
 * Supports proxy connectivity and basic authentication.
 */
@Service
public class CollibraRestClient {

    private static final Logger log = LoggerFactory.getLogger(CollibraRestClient.class);

    private final CollibraApiConfig config;
    private final ObjectMapper objectMapper;
    private WebClient webClient;

    public CollibraRestClient(CollibraApiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (config.isConfigured()) {
            this.webClient = createWebClient();
            log.info("Collibra REST client initialized with base URL: {}", config.getBaseUrl());
        } else {
            log.warn("Collibra integration is not configured. Set collibra.base-url, collibra.username, and collibra.password to enable.");
        }
    }

    private WebClient createWebClient() {
        HttpClient httpClient = createHttpClient();
        String encodedCredentials = createEncodedCredentials();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private HttpClient createHttpClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout())
                .responseTimeout(Duration.ofMillis(config.getReadTimeout()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(config.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(config.getReadTimeout(), TimeUnit.MILLISECONDS)));

        return configureProxy(httpClient);
    }

    private HttpClient configureProxy(HttpClient httpClient) {
        if (!config.isProxyEnabled() || config.getProxyHost() == null || config.getProxyHost().isEmpty()) {
            return httpClient;
        }

        log.info("Configuring proxy for Collibra: {}:{}", config.getProxyHost(), config.getProxyPort());
        return httpClient.proxy(proxy -> {
            ProxyProvider.Builder proxyBuilder = proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(config.getProxyHost())
                    .port(config.getProxyPort());

            if (config.getProxyUsername() != null && !config.getProxyUsername().isEmpty()) {
                proxyBuilder.username(config.getProxyUsername())
                           .password(s -> config.getProxyPassword());
            }
        });
    }

    private String createEncodedCredentials() {
        String credentials = config.getUsername() + ":" + config.getPassword();
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Check if the client is configured and ready to use.
     */
    public boolean isConfigured() {
        return config.isConfigured() && webClient != null;
    }

    /**
     * Import assets to Collibra using JSON multipart upload.
     * This is the primary method for ingesting assets.
     */
    public Mono<CollibraIngestionResult> importAssets(List<CollibraAsset> assets, String assetType) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        if (assets == null || assets.isEmpty()) {
            return Mono.just(CollibraIngestionResult.success(assetType, 0, 0, 0, 0, 0));
        }

        try {
            // Collibra Import API expects an array of assets at the root level, not wrapped in an object
            String jsonPayload = objectMapper.writeValueAsString(assets);
            log.debug("Collibra import payload: {}", jsonPayload);

            // Create multipart request with JSON file
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(jsonPayload.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return "import.json";
                }
            });
            body.add("sendNotification", "false");
            body.add("continueOnError", "true");

            return webClient.post()
                    .uri(config.getImportApiUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(response -> {
                        log.info("Collibra import response: {}", response);
                        String jobId = response.path("id").asText(null);
                        return CollibraIngestionResult.builder()
                                .assetType(assetType)
                                .totalProcessed(assets.size())
                                .assetsCreated(assets.size()) // Will be refined based on actual response
                                .success(true)
                                .message("Import job submitted successfully")
                                .jobId(jobId)
                                .build();
                    })
                    .onErrorResume(e -> handleError(e, assetType));

        } catch (JsonProcessingException e) {
            return handleJsonSerializationError(e, assetType, "import payload");
        }
    }

    /**
     * Create or update assets using the bulk assets API.
     * Alternative method for direct asset creation.
     */
    public Mono<CollibraIngestionResult> bulkCreateAssets(List<CollibraAsset> assets, String assetType) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        if (assets == null || assets.isEmpty()) {
            return Mono.just(CollibraIngestionResult.success(assetType, 0, 0, 0, 0, 0));
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(assets);
            log.debug("Collibra bulk create payload: {}", jsonPayload);

            return webClient.post()
                    .uri(config.getAssetsApiUrl())
                    .bodyValue(assets)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(response -> {
                        log.info("Collibra bulk create response: {}", response);
                        int created = response.isArray() ? response.size() : 0;
                        return CollibraIngestionResult.success(assetType, assets.size(), created, 0, 0, 0);
                    })
                    .onErrorResume(e -> handleError(e, assetType));

        } catch (JsonProcessingException e) {
            return handleJsonSerializationError(e, assetType, "bulk create payload");
        }
    }

    /**
     * Delete an asset from Collibra by its ID.
     */
    public Mono<Boolean> deleteAsset(String assetId) {
        if (!isConfigured()) {
            return Mono.just(false);
        }

        return webClient.delete()
                .uri(config.getBaseUrl() + "/rest/2.0/assets/" + assetId)
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .onErrorResume(e -> {
                    log.error("Failed to delete asset {}: {}", assetId, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Find a community by name.
     */
    public Mono<JsonNode> findCommunity(String communityName) {
        if (!isConfigured()) {
            return Mono.empty();
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/2.0/communities")
                        .queryParam("name", communityName)
                        .queryParam("nameMatchMode", "EXACT")
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnNext(response -> log.debug("Found communities: {}", response))
                .onErrorResume(e -> {
                    log.error("Failed to find community {}: {}", communityName, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Find a domain by name within a community.
     */
    public Mono<JsonNode> findDomain(String domainName, String communityId) {
        if (!isConfigured()) {
            return Mono.empty();
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/2.0/domains")
                        .queryParam("name", domainName)
                        .queryParam("nameMatchMode", "EXACT")
                        .queryParam("communityId", communityId)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnNext(response -> log.debug("Found domains: {}", response))
                .onErrorResume(e -> {
                    log.error("Failed to find domain {}: {}", domainName, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Find an asset type by name.
     */
    public Mono<JsonNode> findAssetType(String assetTypeName) {
        if (!isConfigured()) {
            return Mono.empty();
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/rest/2.0/assetTypes")
                        .queryParam("name", assetTypeName)
                        .queryParam("nameMatchMode", "EXACT")
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnNext(response -> log.debug("Found asset types: {}", response))
                .onErrorResume(e -> {
                    log.error("Failed to find asset type {}: {}", assetTypeName, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Test connectivity to Collibra API.
     */
    public Mono<Boolean> testConnection() {
        if (!isConfigured()) {
            return Mono.just(false);
        }

        return webClient.get()
                .uri("/rest/2.0/application/info")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    log.info("Collibra connection test successful: {}", response);
                    return true;
                })
                .onErrorResume(e -> {
                    log.error("Collibra connection test failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    private Mono<CollibraIngestionResult> handleError(Throwable e, String assetType) {
        if (e instanceof WebClientResponseException wcre) {
            log.error("Collibra API error: {} - {}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
            return Mono.just(CollibraIngestionResult.failure(assetType, 
                    "Collibra API error: " + wcre.getStatusCode() + " - " + wcre.getResponseBodyAsString()));
        }
        log.error("Collibra ingestion error: {}", e.getMessage(), e);
        return Mono.just(CollibraIngestionResult.failure(assetType, "Collibra error: " + e.getMessage()));
    }

    private Mono<CollibraIngestionResult> handleJsonSerializationError(JsonProcessingException e, String assetType, String context) {
        log.error("Failed to serialize {}: {}", context, e.getMessage(), e);
        return Mono.just(CollibraIngestionResult.failure(assetType, "Failed to serialize " + context + ": " + e.getMessage()));
    }
}
