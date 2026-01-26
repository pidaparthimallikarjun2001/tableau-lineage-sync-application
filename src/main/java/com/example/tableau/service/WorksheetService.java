package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauWorkbook;
import com.example.tableau.entity.TableauWorksheet;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.repository.TableauWorkbookRepository;
import com.example.tableau.repository.TableauWorksheetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Service for managing Tableau Worksheet entities.
 */
@Service
public class WorksheetService extends BaseAssetService {

    private final TableauWorksheetRepository worksheetRepository;
    private final TableauWorkbookRepository workbookRepository;
    private final TableauGraphQLClient graphQLClient;
    private final TableauAuthService authService;
    private final ReportAttributeService reportAttributeService;

    public WorksheetService(TableauWorksheetRepository worksheetRepository,
                            TableauWorkbookRepository workbookRepository,
                            TableauGraphQLClient graphQLClient,
                            TableauAuthService authService,
                            ReportAttributeService reportAttributeService) {
        this.worksheetRepository = worksheetRepository;
        this.workbookRepository = workbookRepository;
        this.graphQLClient = graphQLClient;
        this.authService = authService;
        this.reportAttributeService = reportAttributeService;
    }

    /**
     * Get all active worksheets from the database.
     */
    public List<TableauWorksheet> getAllActiveWorksheets() {
        return worksheetRepository.findAllActive();
    }

    /**
     * Get all active worksheets for a site.
     */
    public List<TableauWorksheet> getActiveWorksheetsBySiteId(String siteId) {
        return worksheetRepository.findAllActiveBySiteId(siteId);
    }

    /**
     * Get worksheet by ID.
     */
    public TableauWorksheet getWorksheetById(Long id) {
        return worksheetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worksheet", id.toString()));
    }

    /**
     * Get worksheet by asset ID.
     */
    public Optional<TableauWorksheet> getWorksheetByAssetId(String assetId) {
        return worksheetRepository.findByAssetId(assetId);
    }

    /**
     * Fetch worksheets from Tableau GraphQL API.
     */
    public Mono<List<JsonNode>> fetchWorksheetsFromTableau() {
        return graphQLClient.fetchWorksheets(100);
    }

    /**
     * Ingest or update worksheets from Tableau.
     */
    @Transactional
    public Mono<IngestionResult> ingestWorksheets() {
        String currentSiteId = authService.getCurrentSiteId();
        if (currentSiteId == null) {
            return Mono.just(createFailureResult("Worksheet", "No active site. Please authenticate first."));
        }
        
        return graphQLClient.fetchWorksheets(100)
                .map(worksheets -> {
                    try {
                        int newCount = 0, updatedCount = 0, deletedCount = 0, unchangedCount = 0;
                        Set<String> processedAssetIds = new HashSet<>();
                        
                        for (JsonNode worksheetNode : worksheets) {
                            // For worksheets, use id directly as luid can be null
                            String assetId = worksheetNode.path("id").asText();
                            String name = worksheetNode.path("name").asText();
                            
                            // Workbook info - use id only
                            JsonNode workbookNode = worksheetNode.path("workbook");
                            String workbookId = !workbookNode.isMissingNode() ? 
                                    workbookNode.path("id").asText(null) : null;
                            
                            processedAssetIds.add(assetId);
                            
                            String newHash = generateMetadataHash(assetId, name, workbookId, currentSiteId);
                            
                            // Find the workbook
                            TableauWorkbook workbook = null;
                            if (workbookId != null) {
                                workbook = workbookRepository.findByAssetIdAndSiteId(workbookId, currentSiteId).orElse(null);
                            }
                            
                            Optional<TableauWorksheet> existingWorksheet = worksheetRepository.findByAssetIdAndSiteId(assetId, currentSiteId);
                            
                            if (existingWorksheet.isPresent()) {
                                TableauWorksheet worksheet = existingWorksheet.get();
                                String existingHash = worksheet.getMetadataHash();
                                StatusFlag newStatus = determineStatusFlag(existingHash, newHash, worksheet.getStatusFlag());
                                
                                if (newStatus == StatusFlag.UPDATED) {
                                    worksheet.setName(name);
                                    worksheet.setMetadataHash(newHash);
                                    worksheet.setStatusFlag(StatusFlag.UPDATED);
                                    worksheet.setWorkbook(workbook);
                                    worksheetRepository.save(worksheet);
                                    updatedCount++;
                                    log.info("Updated worksheet: {}", name);
                                } else {
                                    // Use the status determined by determineStatusFlag method
                                    if (worksheet.getStatusFlag() != newStatus && 
                                        worksheet.getStatusFlag() != StatusFlag.DELETED) {
                                        worksheet.setStatusFlag(newStatus);
                                        worksheetRepository.save(worksheet);
                                    }
                                    unchangedCount++;
                                }
                            } else {
                                TableauWorksheet worksheet = TableauWorksheet.builder()
                                        .assetId(assetId)
                                        .siteId(currentSiteId)
                                        .name(name)
                                        .metadataHash(newHash)
                                        .statusFlag(StatusFlag.NEW)
                                        .workbook(workbook)
                                        .build();
                                worksheetRepository.save(worksheet);
                                newCount++;
                                log.info("Created new worksheet: {}", name);
                            }
                        }
                        
                        // Mark worksheets not in Tableau as deleted
                        List<TableauWorksheet> existingWorksheets = worksheetRepository.findAllActiveBySiteId(currentSiteId);
                        for (TableauWorksheet worksheet : existingWorksheets) {
                            if (!processedAssetIds.contains(worksheet.getAssetId())) {
                                softDeleteWorksheetAndChildren(worksheet.getId());
                                deletedCount++;
                                log.info("Soft deleted worksheet: {}", worksheet.getName());
                            }
                        }
                        
                        int total = newCount + updatedCount + unchangedCount;
                        return createSuccessResult("Worksheet", total, newCount, updatedCount, deletedCount, unchangedCount);
                    } catch (Exception e) {
                        log.error("Error ingesting worksheets: {}", e.getMessage(), e);
                        return createFailureResult("Worksheet", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to ingest worksheets: {}", e.getMessage(), e);
                    return Mono.just(createFailureResult("Worksheet", e.getMessage()));
                });
    }

    /**
     * Soft delete a worksheet and cascade to children.
     */
    @Transactional
    public void softDeleteWorksheetAndChildren(Long worksheetId) {
        Optional<TableauWorksheet> worksheetOpt = worksheetRepository.findById(worksheetId);
        if (worksheetOpt.isPresent()) {
            TableauWorksheet worksheet = worksheetOpt.get();
            worksheet.setStatusFlag(StatusFlag.DELETED);
            worksheetRepository.save(worksheet);
            log.info("Soft deleted worksheet: {} and cascading to children", worksheet.getName());
            
            // Cascade to report attributes
            reportAttributeService.softDeleteReportAttributesForWorksheet(worksheetId);
        }
    }

    /**
     * Soft delete all worksheets for a workbook.
     */
    @Transactional
    public void softDeleteWorksheetsForWorkbook(Long workbookId) {
        List<TableauWorksheet> worksheets = worksheetRepository.findByWorkbookDbId(workbookId);
        for (TableauWorksheet worksheet : worksheets) {
            softDeleteWorksheetAndChildren(worksheet.getId());
        }
    }
}
