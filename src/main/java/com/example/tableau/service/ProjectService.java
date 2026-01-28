package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauProject;
import com.example.tableau.entity.TableauSite;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.repository.TableauProjectRepository;
import com.example.tableau.repository.TableauSiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Service for managing Tableau Project entities.
 */
@Service
public class ProjectService extends BaseAssetService {

    private final TableauProjectRepository projectRepository;
    private final TableauSiteRepository siteRepository;
    private final TableauRestClient restClient;
    private final TableauGraphQLClient graphQLClient;
    private final TableauAuthService authService;
    private final WorkbookService workbookService;

    public ProjectService(TableauProjectRepository projectRepository,
                          TableauSiteRepository siteRepository,
                          TableauRestClient restClient,
                          TableauGraphQLClient graphQLClient,
                          TableauAuthService authService,
                          WorkbookService workbookService) {
        this.projectRepository = projectRepository;
        this.siteRepository = siteRepository;
        this.restClient = restClient;
        this.graphQLClient = graphQLClient;
        this.authService = authService;
        this.workbookService = workbookService;
    }

    /**
     * Get all active projects from the database.
     */
    public List<TableauProject> getAllActiveProjects() {
        return projectRepository.findAllActive();
    }

    /**
     * Get all active projects for a site.
     */
    public List<TableauProject> getActiveProjectsBySiteId(String siteId) {
        return projectRepository.findAllActiveBySiteId(siteId);
    }

    /**
     * Get project by ID.
     */
    public TableauProject getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id.toString()));
    }

    /**
     * Get project by asset ID.
     */
    public Optional<TableauProject> getProjectByAssetId(String assetId) {
        return projectRepository.findByAssetId(assetId);
    }

    /**
     * Fetch projects from Tableau REST API.
     */
    public Mono<List<JsonNode>> fetchProjectsFromTableau() {
        return restClient.getProjects();
    }

    /**
     * Ingest or update projects from Tableau.
     */
    @Transactional
    public Mono<IngestionResult> ingestProjects() {
        String currentSiteId = authService.getCurrentSiteId();
        if (currentSiteId == null) {
            return Mono.just(createFailureResult("Project", "No active site. Please authenticate first."));
        }
        
        return restClient.getProjects()
                .map(projects -> {
                    try {
                        int newCount = 0, updatedCount = 0, deletedCount = 0, unchangedCount = 0;
                        Set<String> processedAssetIds = new HashSet<>();
                        
                        TableauSite site = siteRepository.findByAssetId(currentSiteId).orElse(null);
                        
                        for (JsonNode projectNode : projects) {
                            // REST API uses 'id' field for project ID
                            String assetId = projectNode.path("id").asText();
                            String name = projectNode.path("name").asText();
                            String description = projectNode.path("description").asText(null);
                            
                            // Extract parent project ID from REST API
                            String parentProjectId = projectNode.path("parentProjectId").asText(null);
                            
                            // Extract owner information from REST API
                            // With fields=_all_, REST API returns owner as { "id": "...", "name": "..." }
                            JsonNode ownerNode = projectNode.path("owner");
                            String owner = null;
                            if (!ownerNode.isMissingNode() && !ownerNode.isNull()) {
                                // Try to get owner name, fall back to ID if name not available
                                owner = ownerNode.path("name").asText(ownerNode.path("id").asText(null));
                            }
                            
                            if (owner == null || owner.isEmpty()) {
                                log.warn("Project {} (ID: {}) has no owner - this should not happen in Tableau UI", name, assetId);
                            }
                            
                            processedAssetIds.add(assetId);
                            
                            String newHash = generateMetadataHash(assetId, name, description, parentProjectId, owner, currentSiteId);
                            
                            Optional<TableauProject> existingProject = projectRepository.findByAssetIdAndSiteId(assetId, currentSiteId);
                            
                            if (existingProject.isPresent()) {
                                TableauProject project = existingProject.get();
                                String existingHash = project.getMetadataHash();
                                StatusFlag newStatus = determineStatusFlag(existingHash, newHash, project.getStatusFlag());
                                
                                if (newStatus == StatusFlag.UPDATED) {
                                    project.setName(name);
                                    project.setDescription(description);
                                    project.setParentProjectId(parentProjectId);
                                    project.setOwner(owner);
                                    project.setMetadataHash(newHash);
                                    project.setStatusFlag(StatusFlag.UPDATED);
                                    if (site != null) {
                                        project.setSite(site);
                                    }
                                    projectRepository.save(project);
                                    updatedCount++;
                                    log.info("Updated project: {}", name);
                                } else {
                                    // Use the status determined by determineStatusFlag method
                                    if (project.getStatusFlag() != newStatus && 
                                        project.getStatusFlag() != StatusFlag.DELETED) {
                                        project.setStatusFlag(newStatus);
                                        projectRepository.save(project);
                                    }
                                    unchangedCount++;
                                }
                            } else {
                                TableauProject project = TableauProject.builder()
                                        .assetId(assetId)
                                        .siteId(currentSiteId)
                                        .name(name)
                                        .description(description)
                                        .parentProjectId(parentProjectId)
                                        .owner(owner)
                                        .metadataHash(newHash)
                                        .statusFlag(StatusFlag.NEW)
                                        .site(site)
                                        .build();
                                projectRepository.save(project);
                                newCount++;
                                log.info("Created new project: {}", name);
                            }
                        }
                        
                        // Mark projects not in Tableau as deleted
                        List<TableauProject> existingProjects = projectRepository.findAllActiveBySiteId(currentSiteId);
                        for (TableauProject project : existingProjects) {
                            if (!processedAssetIds.contains(project.getAssetId())) {
                                softDeleteProjectAndChildren(project.getId());
                                deletedCount++;
                                log.info("Soft deleted project: {}", project.getName());
                            }
                        }
                        
                        int total = newCount + updatedCount + unchangedCount;
                        return createSuccessResult("Project", total, newCount, updatedCount, deletedCount, unchangedCount);
                    } catch (Exception e) {
                        log.error("Error ingesting projects: {}", e.getMessage(), e);
                        return createFailureResult("Project", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to ingest projects: {}", e.getMessage(), e);
                    return Mono.just(createFailureResult("Project", e.getMessage()));
                });
    }

    /**
     * Soft delete a project and cascade to children.
     */
    @Transactional
    public void softDeleteProjectAndChildren(Long projectId) {
        Optional<TableauProject> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            TableauProject project = projectOpt.get();
            project.setStatusFlag(StatusFlag.DELETED);
            projectRepository.save(project);
            log.info("Soft deleted project: {} and cascading to children", project.getName());
            
            // Cascade to workbooks
            workbookService.softDeleteWorkbooksForProject(projectId);
        }
    }

    /**
     * Soft delete all projects for a site.
     */
    @Transactional
    public void softDeleteProjectsForSite(String siteId) {
        List<TableauProject> projects = projectRepository.findBySiteId(siteId);
        for (TableauProject project : projects) {
            softDeleteProjectAndChildren(project.getId());
        }
    }
}
