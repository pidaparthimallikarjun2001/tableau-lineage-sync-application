package com.example.tableau.service;

import com.example.tableau.config.TableauApiConfig;
import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauServer;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.repository.TableauServerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing Tableau Server entities.
 */
@Service
public class ServerService extends BaseAssetService {

    private final TableauServerRepository serverRepository;
    private final TableauRestClient restClient;
    private final TableauApiConfig apiConfig;
    private final SiteService siteService;

    public ServerService(TableauServerRepository serverRepository,
                         TableauRestClient restClient,
                         TableauApiConfig apiConfig,
                         SiteService siteService) {
        this.serverRepository = serverRepository;
        this.restClient = restClient;
        this.apiConfig = apiConfig;
        this.siteService = siteService;
    }

    /**
     * Get all active servers from the database.
     */
    public List<TableauServer> getAllActiveServers() {
        return serverRepository.findAllActive();
    }

    /**
     * Get server by ID.
     */
    public TableauServer getServerById(Long id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Server", id.toString()));
    }

    /**
     * Get server by asset ID.
     */
    public Optional<TableauServer> getServerByAssetId(String assetId) {
        return serverRepository.findByAssetId(assetId);
    }

    /**
     * Fetch server info from Tableau.
     */
    public Mono<JsonNode> fetchServerInfoFromTableau() {
        return restClient.getServerInfo();
    }

    /**
     * Ingest or update server information from Tableau.
     */
    @Transactional
    public Mono<IngestionResult> ingestServer() {
        return restClient.getServerInfo()
                .map(serverInfo -> {
                    try {
                        JsonNode productVersion = serverInfo.path("serverInfo").path("productVersion");
                        String version = productVersion.path("value").asText("unknown");
                        String build = productVersion.path("build").asText("");
                        
                        // Use the configured base URL as the server identifier
                        String serverUrl = apiConfig.getBaseUrl();
                        String assetId = generateServerAssetId(serverUrl);
                        String name = extractServerName(serverUrl);
                        
                        // Generate metadata hash for change detection
                        String newHash = generateMetadataHash(assetId, name, serverUrl, version, build);
                        
                        // Check if server exists
                        Optional<TableauServer> existingServer = serverRepository.findByAssetId(assetId);
                        
                        int newCount = 0, updatedCount = 0, unchangedCount = 0;
                        
                        if (existingServer.isPresent()) {
                            TableauServer server = existingServer.get();
                            String existingHash = server.getMetadataHash();
                            StatusFlag newStatus = determineStatusFlag(existingHash, newHash, server.getStatusFlag());
                            
                            if (newStatus == StatusFlag.UPDATED) {
                                server.setName(name);
                                server.setServerUrl(serverUrl);
                                server.setVersion(version + " (build " + build + ")");
                                server.setMetadataHash(newHash);
                                server.setStatusFlag(StatusFlag.UPDATED);
                                server.setCollibraSyncStatus(determineCollibraSyncStatus(StatusFlag.UPDATED, server.getCollibraSyncStatus()));
                                serverRepository.save(server);
                                updatedCount = 1;
                                log.info("Updated server: {}", name);
                            } else {
                                // Use the status determined by determineStatusFlag method
                                if (server.getStatusFlag() != newStatus) {
                                    server.setStatusFlag(newStatus);
                                    server.setCollibraSyncStatus(determineCollibraSyncStatus(newStatus, server.getCollibraSyncStatus()));
                                    serverRepository.save(server);
                                }
                                unchangedCount = 1;
                                log.debug("Server unchanged: {}", name);
                            }
                        } else {
                            // New server
                            TableauServer server = TableauServer.builder()
                                    .assetId(assetId)
                                    .name(name)
                                    .serverUrl(serverUrl)
                                    .version(version + " (build " + build + ")")
                                    .metadataHash(newHash)
                                    .statusFlag(StatusFlag.NEW)
                                    .build();
                            serverRepository.save(server);
                            newCount = 1;
                            log.info("Created new server: {}", name);
                        }
                        
                        return createSuccessResult("Server", 1, newCount, updatedCount, 0, unchangedCount);
                    } catch (Exception e) {
                        log.error("Error ingesting server: {}", e.getMessage(), e);
                        return createFailureResult("Server", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to ingest server: {}", e.getMessage(), e);
                    return Mono.just(createFailureResult("Server", e.getMessage()));
                });
    }

    /**
     * Soft delete a server and cascade to children.
     */
    @Transactional
    public void softDeleteServerAndChildren(Long serverId) {
        Optional<TableauServer> serverOpt = serverRepository.findById(serverId);
        if (serverOpt.isPresent()) {
            TableauServer server = serverOpt.get();
            server.setStatusFlag(StatusFlag.DELETED);
            server.setCollibraSyncStatus(determineCollibraSyncStatus(StatusFlag.DELETED, server.getCollibraSyncStatus()));
            serverRepository.save(server);
            log.info("Soft deleted server: {} and cascading to children", server.getName());
            
            // Cascade to sites
            siteService.softDeleteSitesForServer(serverId);
        }
    }

    private String generateServerAssetId(String serverUrl) {
        // Create a deterministic ID from the server URL
        return "server-" + serverUrl.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase();
    }

    private String extractServerName(String serverUrl) {
        // Extract a readable name from the URL
        try {
            String host = serverUrl.replace("https://", "").replace("http://", "");
            if (host.contains("/")) {
                host = host.substring(0, host.indexOf("/"));
            }
            return host;
        } catch (Exception e) {
            return serverUrl;
        }
    }
}
