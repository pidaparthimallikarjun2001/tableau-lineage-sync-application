package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauProject;
import com.example.tableau.entity.TableauWorkbook;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.repository.TableauProjectRepository;
import com.example.tableau.repository.TableauWorkbookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing Tableau Workbook entities.
 */
@Service
public class WorkbookService extends BaseAssetService {

    private final TableauWorkbookRepository workbookRepository;
    private final TableauProjectRepository projectRepository;
    private final TableauGraphQLClient graphQLClient;
    private final TableauAuthService authService;
    private final WorksheetService worksheetService;
    private final DataSourceService dataSourceService;

    public WorkbookService(TableauWorkbookRepository workbookRepository,
                           TableauProjectRepository projectRepository,
                           TableauGraphQLClient graphQLClient,
                           TableauAuthService authService,
                           WorksheetService worksheetService,
                           DataSourceService dataSourceService) {
        this.workbookRepository = workbookRepository;
        this.projectRepository = projectRepository;
        this.graphQLClient = graphQLClient;
        this.authService = authService;
        this.worksheetService = worksheetService;
        this.dataSourceService = dataSourceService;
    }

    /**
     * Get all active workbooks from the database.
     */
    public List<TableauWorkbook> getAllActiveWorkbooks() {
        return workbookRepository.findAllActive();
    }

    /**
     * Get all active workbooks for a site.
     */
    public List<TableauWorkbook> getActiveWorkbooksBySiteId(String siteId) {
        return workbookRepository.findAllActiveBySiteId(siteId);
    }

    /**
     * Get workbook by ID.
     */
    public TableauWorkbook getWorkbookById(Long id) {
        return workbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workbook", id.toString()));
    }

    /**
     * Get workbook by asset ID.
     */
    public Optional<TableauWorkbook> getWorkbookByAssetId(String assetId) {
        return workbookRepository.findByAssetId(assetId);
    }

    /**
     * Fetch workbooks from Tableau GraphQL API.
     */
    public Mono<List<JsonNode>> fetchWorkbooksFromTableau() {
        return graphQLClient.fetchWorkbooks(100);
    }

    /**
     * Ingest or update workbooks from Tableau.
     */
    @Transactional
    public Mono<IngestionResult> ingestWorkbooks() {
        String currentSiteId = authService.getCurrentSiteId();
        if (currentSiteId == null) {
            return Mono.just(createFailureResult("Workbook", "No active site. Please authenticate first."));
        }
        
        return graphQLClient.fetchWorkbooks(100)
                .map(workbooks -> {
                    try {
                        int newCount = 0, updatedCount = 0, deletedCount = 0, unchangedCount = 0;
                        Set<String> processedAssetIds = new HashSet<>();
                        
                        for (JsonNode workbookNode : workbooks) {
                            String assetId = workbookNode.path("luid").asText(workbookNode.path("id").asText());
                            String name = workbookNode.path("name").asText();
                            String description = workbookNode.path("description").asText(null);
                            String projectName = workbookNode.path("projectName").asText(null);
                            String contentUrl = workbookNode.path("uri").asText(null);
                            
                            // Parse dates
                            LocalDateTime createdAt = parseDateTime(workbookNode.path("createdAt").asText(null));
                            LocalDateTime updatedAt = parseDateTime(workbookNode.path("updatedAt").asText(null));
                            
                            // Owner info
                            JsonNode ownerNode = workbookNode.path("owner");
                            String owner = !ownerNode.isMissingNode() ? ownerNode.path("username").asText(ownerNode.path("name").asText(null)) : null;
                            String ownerId = !ownerNode.isMissingNode() ? ownerNode.path("id").asText(null) : null;
                            
                            processedAssetIds.add(assetId);
                            
                            String newHash = generateMetadataHash(assetId, name, description, projectName, 
                                    owner, contentUrl, currentSiteId);
                            
                            // Find the project
                            TableauProject project = null;
                            if (projectName != null) {
                                List<TableauProject> projects = projectRepository.findAllActiveBySiteId(currentSiteId);
                                project = projects.stream()
                                        .filter(p -> projectName.equals(p.getName()))
                                        .findFirst().orElse(null);
                            }
                            
                            Optional<TableauWorkbook> existingWorkbook = workbookRepository.findByAssetIdAndSiteId(assetId, currentSiteId);
                            
                            if (existingWorkbook.isPresent()) {
                                TableauWorkbook workbook = existingWorkbook.get();
                                String existingHash = workbook.getMetadataHash();
                                StatusFlag newStatus = determineStatusFlag(existingHash, newHash, workbook.getStatusFlag());
                                
                                if (newStatus == StatusFlag.UPDATED) {
                                    workbook.setName(name);
                                    workbook.setDescription(description);
                                    workbook.setOwner(owner);
                                    workbook.setOwnerId(ownerId);
                                    workbook.setContentUrl(contentUrl);
                                    workbook.setTableauCreatedAt(createdAt);
                                    workbook.setTableauUpdatedAt(updatedAt);
                                    workbook.setMetadataHash(newHash);
                                    workbook.setStatusFlag(StatusFlag.UPDATED);
                                    workbook.setProject(project);
                                    workbookRepository.save(workbook);
                                    updatedCount++;
                                    log.info("Updated workbook: {}", name);
                                } else {
                                    // Use the status determined by determineStatusFlag method
                                    if (workbook.getStatusFlag() != newStatus && 
                                        workbook.getStatusFlag() != StatusFlag.DELETED) {
                                        workbook.setStatusFlag(newStatus);
                                        workbookRepository.save(workbook);
                                    }
                                    unchangedCount++;
                                }
                            } else {
                                TableauWorkbook workbook = TableauWorkbook.builder()
                                        .assetId(assetId)
                                        .siteId(currentSiteId)
                                        .name(name)
                                        .description(description)
                                        .owner(owner)
                                        .ownerId(ownerId)
                                        .contentUrl(contentUrl)
                                        .tableauCreatedAt(createdAt)
                                        .tableauUpdatedAt(updatedAt)
                                        .metadataHash(newHash)
                                        .statusFlag(StatusFlag.NEW)
                                        .project(project)
                                        .build();
                                workbookRepository.save(workbook);
                                newCount++;
                                log.info("Created new workbook: {}", name);
                            }
                        }
                        
                        // Mark workbooks not in Tableau as deleted
                        List<TableauWorkbook> existingWorkbooks = workbookRepository.findAllActiveBySiteId(currentSiteId);
                        for (TableauWorkbook workbook : existingWorkbooks) {
                            if (!processedAssetIds.contains(workbook.getAssetId())) {
                                softDeleteWorkbookAndChildren(workbook.getId());
                                deletedCount++;
                                log.info("Soft deleted workbook: {}", workbook.getName());
                            }
                        }
                        
                        int total = newCount + updatedCount + unchangedCount;
                        return createSuccessResult("Workbook", total, newCount, updatedCount, deletedCount, unchangedCount);
                    } catch (Exception e) {
                        log.error("Error ingesting workbooks: {}", e.getMessage(), e);
                        return createFailureResult("Workbook", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to ingest workbooks: {}", e.getMessage(), e);
                    return Mono.just(createFailureResult("Workbook", e.getMessage()));
                });
    }

    /**
     * Soft delete a workbook and cascade to children.
     */
    @Transactional
    public void softDeleteWorkbookAndChildren(Long workbookId) {
        Optional<TableauWorkbook> workbookOpt = workbookRepository.findById(workbookId);
        if (workbookOpt.isPresent()) {
            TableauWorkbook workbook = workbookOpt.get();
            workbook.setStatusFlag(StatusFlag.DELETED);
            workbookRepository.save(workbook);
            log.info("Soft deleted workbook: {} and cascading to children", workbook.getName());
            
            // Cascade to worksheets and embedded data sources
            worksheetService.softDeleteWorksheetsForWorkbook(workbookId);
            dataSourceService.softDeleteDataSourcesForWorkbook(workbookId);
        }
    }

    /**
     * Soft delete all workbooks for a project.
     */
    @Transactional
    public void softDeleteWorkbooksForProject(Long projectId) {
        List<TableauWorkbook> workbooks = workbookRepository.findByProjectDbId(projectId);
        for (TableauWorkbook workbook : workbooks) {
            softDeleteWorkbookAndChildren(workbook.getId());
        }
    }
}
