package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauServer;
import com.example.tableau.entity.TableauSite;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.repository.TableauServerRepository;
import com.example.tableau.repository.TableauSiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Service for managing Tableau Site entities.
 */
@Service
public class SiteService extends BaseAssetService {

    private final TableauSiteRepository siteRepository;
    private final TableauServerRepository serverRepository;
    private final TableauRestClient restClient;
    private final ProjectService projectService;

    public SiteService(TableauSiteRepository siteRepository,
                       TableauServerRepository serverRepository,
                       TableauRestClient restClient,
                       ProjectService projectService) {
        this.siteRepository = siteRepository;
        this.serverRepository = serverRepository;
        this.restClient = restClient;
        this.projectService = projectService;
    }

    /**
     * Get all active sites from the database.
     */
    public List<TableauSite> getAllActiveSites() {
        return siteRepository.findAllActive();
    }

    /**
     * Get site by ID.
     */
    public TableauSite getSiteById(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id.toString()));
    }

    /**
     * Get site by asset ID.
     */
    public Optional<TableauSite> getSiteByAssetId(String assetId) {
        return siteRepository.findByAssetId(assetId);
    }

    /**
     * Get site by content URL.
     */
    public Optional<TableauSite> getSiteByContentUrl(String contentUrl) {
        return siteRepository.findByContentUrl(contentUrl);
    }

    /**
     * Fetch sites from Tableau REST API.
     */
    public Mono<List<JsonNode>> fetchSitesFromTableau() {
        return restClient.getSites();
    }

    /**
     * Ingest or update sites from Tableau.
     */
    @Transactional
    public Mono<IngestionResult> ingestSites() {
        return restClient.getSites()
                .map(sites -> {
                    try {
                        int newCount = 0, updatedCount = 0, deletedCount = 0, unchangedCount = 0;
                        Set<String> processedAssetIds = new HashSet<>();
                        
                        // Get the default server (or first available)
                        TableauServer server = serverRepository.findAllActive().stream()
                                .findFirst().orElse(null);
                        
                        for (JsonNode siteNode : sites) {
                            String assetId = siteNode.path("id").asText();
                            String name = siteNode.path("name").asText();
                            String contentUrl = siteNode.path("contentUrl").asText("");
                            
                            processedAssetIds.add(assetId);
                            
                            // Build site URL from server URL and content URL
                            String siteUrl = null;
                            if (server != null && server.getServerUrl() != null && !contentUrl.isEmpty()) {
                                siteUrl = server.getServerUrl() + "/#/site/" + contentUrl + "/";
                            }
                            
                            String newHash = generateMetadataHash(assetId, name, contentUrl);
                            
                            Optional<TableauSite> existingSite = siteRepository.findByAssetId(assetId);
                            
                            if (existingSite.isPresent()) {
                                TableauSite site = existingSite.get();
                                String existingHash = site.getMetadataHash();
                                StatusFlag newStatus = determineStatusFlag(existingHash, newHash, site.getStatusFlag());
                                
                                if (newStatus == StatusFlag.UPDATED) {
                                    site.setName(name);
                                    site.setContentUrl(contentUrl);
                                    site.setSiteUrl(siteUrl);
                                    site.setMetadataHash(newHash);
                                    site.setStatusFlag(StatusFlag.UPDATED);
                                    site.setCollibraSyncStatus(determineCollibraSyncStatus(StatusFlag.UPDATED, site.getCollibraSyncStatus()));
                                    if (server != null) {
                                        site.setServer(server);
                                    }
                                    siteRepository.save(site);
                                    updatedCount++;
                                    log.info("Updated site: {}", name);
                                } else {
                                    // Use the status determined by determineStatusFlag method
                                    if (site.getStatusFlag() != newStatus && 
                                        site.getStatusFlag() != StatusFlag.DELETED) {
                                        site.setStatusFlag(newStatus);
                                        site.setCollibraSyncStatus(determineCollibraSyncStatus(newStatus, site.getCollibraSyncStatus()));
                                        siteRepository.save(site);
                                    }
                                    unchangedCount++;
                                }
                            } else {
                                TableauSite site = TableauSite.builder()
                                        .assetId(assetId)
                                        .name(name)
                                        .contentUrl(contentUrl)
                                        .siteUrl(siteUrl)
                                        .metadataHash(newHash)
                                        .statusFlag(StatusFlag.NEW)
                                        .server(server)
                                        .build();
                                siteRepository.save(site);
                                newCount++;
                                log.info("Created new site: {}", name);
                            }
                        }
                        
                        // Mark sites not in Tableau as deleted
                        List<TableauSite> existingSites = siteRepository.findByStatusFlagNot(StatusFlag.DELETED);
                        for (TableauSite site : existingSites) {
                            if (!processedAssetIds.contains(site.getAssetId())) {
                                softDeleteSiteAndChildren(site.getId());
                                deletedCount++;
                                log.info("Soft deleted site: {}", site.getName());
                            }
                        }
                        
                        int total = newCount + updatedCount + unchangedCount;
                        return createSuccessResult("Site", total, newCount, updatedCount, deletedCount, unchangedCount);
                    } catch (Exception e) {
                        log.error("Error ingesting sites: {}", e.getMessage(), e);
                        return createFailureResult("Site", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to ingest sites: {}", e.getMessage(), e);
                    return Mono.just(createFailureResult("Site", e.getMessage()));
                });
    }

    /**
     * Soft delete a site and cascade to children.
     */
    @Transactional
    public void softDeleteSiteAndChildren(Long siteId) {
        Optional<TableauSite> siteOpt = siteRepository.findById(siteId);
        if (siteOpt.isPresent()) {
            TableauSite site = siteOpt.get();
            site.setStatusFlag(StatusFlag.DELETED);
            site.setCollibraSyncStatus(determineCollibraSyncStatus(StatusFlag.DELETED, site.getCollibraSyncStatus()));
            siteRepository.save(site);
            log.info("Soft deleted site: {} and cascading to children", site.getName());
            
            // Cascade to projects
            projectService.softDeleteProjectsForSite(site.getAssetId());
        }
    }

    /**
     * Soft delete all sites for a server.
     */
    @Transactional
    public void softDeleteSitesForServer(Long serverId) {
        List<TableauSite> sites = siteRepository.findByServerId(serverId);
        for (TableauSite site : sites) {
            softDeleteSiteAndChildren(site.getId());
        }
    }
}
