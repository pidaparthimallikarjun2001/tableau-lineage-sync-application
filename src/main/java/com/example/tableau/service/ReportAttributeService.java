package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.ReportAttribute;
import com.example.tableau.entity.TableauDataSource;
import com.example.tableau.entity.TableauWorksheet;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.repository.ReportAttributeRepository;
import com.example.tableau.repository.TableauDataSourceRepository;
import com.example.tableau.repository.TableauWorksheetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Service for managing Report Attribute (Sheet Field Instance) entities.
 * These represent fields used in worksheets with their source and calculation information.
 */
@Service
public class ReportAttributeService extends BaseAssetService {

    private final ReportAttributeRepository reportAttributeRepository;
    private final TableauWorksheetRepository worksheetRepository;
    private final TableauDataSourceRepository dataSourceRepository;
    private final TableauGraphQLClient graphQLClient;
    private final TableauAuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportAttributeService(ReportAttributeRepository reportAttributeRepository,
                                   TableauWorksheetRepository worksheetRepository,
                                   TableauDataSourceRepository dataSourceRepository,
                                   TableauGraphQLClient graphQLClient,
                                   TableauAuthService authService) {
        this.reportAttributeRepository = reportAttributeRepository;
        this.worksheetRepository = worksheetRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.graphQLClient = graphQLClient;
        this.authService = authService;
    }

    /**
     * Get all active report attributes from the database.
     */
    public List<ReportAttribute> getAllActiveReportAttributes() {
        return reportAttributeRepository.findAllActive();
    }

    /**
     * Get all active report attributes for a site.
     */
    public List<ReportAttribute> getActiveReportAttributesBySiteId(String siteId) {
        return reportAttributeRepository.findAllActiveBySiteId(siteId);
    }

    /**
     * Get all calculated fields.
     */
    public List<ReportAttribute> getCalculatedFields() {
        return reportAttributeRepository.findByIsCalculatedTrue();
    }

    /**
     * Get report attribute by ID.
     */
    public ReportAttribute getReportAttributeById(Long id) {
        return reportAttributeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReportAttribute", id.toString()));
    }

    /**
     * Get report attribute by asset ID.
     */
    public Optional<ReportAttribute> getReportAttributeByAssetId(String assetId) {
        return reportAttributeRepository.findByAssetId(assetId);
    }

    /**
     * Fetch sheet field instances from Tableau GraphQL API.
     */
    public Mono<List<JsonNode>> fetchReportAttributesFromTableau() {
        return graphQLClient.fetchSheetFieldInstances(100);
    }

    /**
     * Ingest or update report attributes from Tableau.
     */
    @Transactional
    public Mono<IngestionResult> ingestReportAttributes() {
        String currentSiteId = authService.getCurrentSiteId();
        if (currentSiteId == null) {
            return Mono.just(createFailureResult("ReportAttribute", "No active site. Please authenticate first."));
        }
        
        return graphQLClient.fetchSheetFieldInstances(100)
                .map(fieldInstances -> {
                    try {
                        int newCount = 0, updatedCount = 0, deletedCount = 0, unchangedCount = 0;
                        Set<String> processedKeys = new HashSet<>();
                        
                        for (JsonNode fieldNode : fieldInstances) {
                            String assetId = fieldNode.path("id").asText();
                            String name = fieldNode.path("name").asText();
                            
                            // Extract field role from sheetFieldInstance
                            String fieldRole = fieldNode.path("role").asText(null);
                            
                            // Sheet info - use id directly for worksheets
                            JsonNode sheetNode = fieldNode.path("sheet");
                            String worksheetId = !sheetNode.isMissingNode() ? 
                                    sheetNode.path("id").asText(null) : null;
                            
                            // Datasource info - use id only
                            JsonNode dsNode = fieldNode.path("datasource");
                            String datasourceId = !dsNode.isMissingNode() ? 
                                    dsNode.path("id").asText(null) : null;
                            String datasourceName = !dsNode.isMissingNode() ? dsNode.path("name").asText(null) : null;
                            
                            // Extract lineage information
                            String lineageInfo = extractLineageInfo(fieldNode);
                            
                            // Check for calculations
                            boolean isCalculated = false;
                            String calculationLogic = null;
                            JsonNode calculations = fieldNode.path("referencedByCalculations");
                            if (calculations.isArray() && !calculations.isEmpty()) {
                                isCalculated = true;
                                calculationLogic = extractCalculationLogic(calculations);
                            }
                            
                            // Extract upstream field info
                            JsonNode upstreamFields = fieldNode.path("upstreamFields");
                            String dataType = null;
                            String sourceColumnName = null;
                            String sourceTableName = null;
                            if (upstreamFields.isArray() && !upstreamFields.isEmpty()) {
                                JsonNode firstUpstream = upstreamFields.get(0);
                                dataType = firstUpstream.path("dataType").asText(null);
                                // Check if field is a CalculatedField by __typename
                                String fieldType = firstUpstream.path("__typename").asText("");
                                if ("CalculatedField".equals(fieldType)) {
                                    isCalculated = true;
                                    calculationLogic = firstUpstream.path("formula").asText(calculationLogic);
                                }
                                
                                // Get column and table info
                                JsonNode upstreamColumns = firstUpstream.path("upstreamColumns");
                                if (upstreamColumns.isArray() && !upstreamColumns.isEmpty()) {
                                    JsonNode firstColumn = upstreamColumns.get(0);
                                    sourceColumnName = firstColumn.path("name").asText(null);
                                    JsonNode table = firstColumn.path("table");
                                    if (!table.isMissingNode()) {
                                        sourceTableName = table.path("fullName").asText(table.path("name").asText(null));
                                    }
                                }
                            }
                            
                            // Unique key is assetId + worksheetId + siteId
                            String uniqueKey = assetId + "|" + worksheetId + "|" + currentSiteId;
                            processedKeys.add(uniqueKey);
                            
                            String newHash = generateMetadataHash(assetId, name, worksheetId, datasourceId, fieldRole,
                                    String.valueOf(isCalculated), calculationLogic, lineageInfo, currentSiteId);
                            
                            // Find related entities
                            TableauWorksheet worksheet = worksheetId != null ? 
                                    worksheetRepository.findByAssetIdAndSiteId(worksheetId, currentSiteId).orElse(null) : null;
                            TableauDataSource dataSource = datasourceId != null ? 
                                    dataSourceRepository.findByAssetIdAndSiteId(datasourceId, currentSiteId).orElse(null) : null;
                            
                            Optional<ReportAttribute> existingAttr = reportAttributeRepository
                                    .findByAssetIdAndWorksheetIdAndSiteId(assetId, worksheetId, currentSiteId);
                            
                            if (existingAttr.isPresent()) {
                                ReportAttribute attr = existingAttr.get();
                                String existingHash = attr.getMetadataHash();
                                StatusFlag newStatus = determineStatusFlag(existingHash, newHash, attr.getStatusFlag());
                                
                                if (newStatus == StatusFlag.UPDATED) {
                                    attr.setName(name);
                                    attr.setDataType(dataType);
                                    attr.setFieldRole(fieldRole);
                                    attr.setIsCalculated(isCalculated);
                                    attr.setCalculationLogic(calculationLogic);
                                    attr.setSourceDatasourceId(datasourceId);
                                    attr.setSourceDatasourceName(datasourceName);
                                    attr.setSourceColumnName(sourceColumnName);
                                    attr.setSourceTableName(sourceTableName);
                                    attr.setLineageInfo(lineageInfo);
                                    attr.setMetadataHash(newHash);
                                    attr.setStatusFlag(StatusFlag.UPDATED);
                                    attr.setWorksheet(worksheet);
                                    attr.setDataSource(dataSource);
                                    reportAttributeRepository.save(attr);
                                    updatedCount++;
                                    log.info("Updated report attribute: {}", name);
                                } else {
                                    // Use the status determined by determineStatusFlag method
                                    if (attr.getStatusFlag() != newStatus && 
                                        attr.getStatusFlag() != StatusFlag.DELETED) {
                                        attr.setStatusFlag(newStatus);
                                        reportAttributeRepository.save(attr);
                                    }
                                    unchangedCount++;
                                }
                            } else {
                                ReportAttribute attr = ReportAttribute.builder()
                                        .assetId(assetId)
                                        .worksheetId(worksheetId)
                                        .siteId(currentSiteId)
                                        .name(name)
                                        .dataType(dataType)
                                        .fieldRole(fieldRole)
                                        .isCalculated(isCalculated)
                                        .calculationLogic(calculationLogic)
                                        .sourceDatasourceId(datasourceId)
                                        .sourceDatasourceName(datasourceName)
                                        .sourceColumnName(sourceColumnName)
                                        .sourceTableName(sourceTableName)
                                        .lineageInfo(lineageInfo)
                                        .metadataHash(newHash)
                                        .statusFlag(StatusFlag.NEW)
                                        .worksheet(worksheet)
                                        .dataSource(dataSource)
                                        .build();
                                reportAttributeRepository.save(attr);
                                newCount++;
                                log.info("Created new report attribute: {}", name);
                            }
                        }
                        
                        // Mark attributes not in Tableau as deleted
                        List<ReportAttribute> existingAttrs = reportAttributeRepository.findAllActiveBySiteId(currentSiteId);
                        for (ReportAttribute attr : existingAttrs) {
                            String key = attr.getAssetId() + "|" + attr.getWorksheetId() + "|" + currentSiteId;
                            if (!processedKeys.contains(key)) {
                                attr.setStatusFlag(StatusFlag.DELETED);
                                reportAttributeRepository.save(attr);
                                deletedCount++;
                                log.info("Soft deleted report attribute: {}", attr.getName());
                            }
                        }
                        
                        int total = newCount + updatedCount + unchangedCount;
                        return createSuccessResult("ReportAttribute", total, newCount, updatedCount, deletedCount, unchangedCount);
                    } catch (Exception e) {
                        log.error("Error ingesting report attributes: {}", e.getMessage(), e);
                        return createFailureResult("ReportAttribute", e.getMessage());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to ingest report attributes: {}", e.getMessage(), e);
                    return Mono.just(createFailureResult("ReportAttribute", e.getMessage()));
                });
    }

    /**
     * Soft delete all report attributes for a worksheet.
     */
    @Transactional
    public void softDeleteReportAttributesForWorksheet(Long worksheetId) {
        List<ReportAttribute> attrs = reportAttributeRepository.findByWorksheetDbId(worksheetId);
        for (ReportAttribute attr : attrs) {
            attr.setStatusFlag(StatusFlag.DELETED);
            reportAttributeRepository.save(attr);
        }
    }

    private String extractLineageInfo(JsonNode fieldNode) {
        try {
            Map<String, Object> lineage = new HashMap<>();
            
            // Upstream fields
            JsonNode upstreamFields = fieldNode.path("upstreamFields");
            if (upstreamFields.isArray() && !upstreamFields.isEmpty()) {
                List<Map<String, Object>> fields = new ArrayList<>();
                for (JsonNode field : upstreamFields) {
                    Map<String, Object> fieldInfo = new HashMap<>();
                    fieldInfo.put("id", field.path("id").asText());
                    fieldInfo.put("name", field.path("name").asText());
                    fieldInfo.put("dataType", field.path("dataType").asText());
                    // Check if field is calculated based on __typename
                    String fieldType = field.path("__typename").asText("");
                    boolean isCalc = "CalculatedField".equals(fieldType);
                    fieldInfo.put("isCalculated", isCalc);
                    if (isCalc) {
                        fieldInfo.put("formula", field.path("formula").asText());
                    }
                    fields.add(fieldInfo);
                }
                lineage.put("upstreamFields", fields);
            }
            
            // Upstream tables
            JsonNode upstreamTables = fieldNode.path("upstreamTables");
            if (upstreamTables.isArray() && !upstreamTables.isEmpty()) {
                List<Map<String, Object>> tables = new ArrayList<>();
                for (JsonNode table : upstreamTables) {
                    Map<String, Object> tableInfo = new HashMap<>();
                    tableInfo.put("id", table.path("id").asText());
                    tableInfo.put("name", table.path("name").asText());
                    tableInfo.put("fullName", table.path("fullName").asText());
                    tableInfo.put("schema", table.path("schema").asText());
                    
                    JsonNode database = table.path("database");
                    if (!database.isMissingNode()) {
                        Map<String, Object> dbInfo = new HashMap<>();
                        dbInfo.put("id", database.path("id").asText());
                        dbInfo.put("name", database.path("name").asText());
                        dbInfo.put("connectionType", database.path("connectionType").asText());
                        tableInfo.put("database", dbInfo);
                    }
                    tables.add(tableInfo);
                }
                lineage.put("upstreamTables", tables);
            }
            
            // Upstream columns
            JsonNode upstreamColumns = fieldNode.path("upstreamColumns");
            if (upstreamColumns.isArray() && !upstreamColumns.isEmpty()) {
                List<Map<String, Object>> columns = new ArrayList<>();
                for (JsonNode column : upstreamColumns) {
                    Map<String, Object> colInfo = new HashMap<>();
                    colInfo.put("id", column.path("id").asText());
                    colInfo.put("name", column.path("name").asText());
                    colInfo.put("remoteType", column.path("remoteType").asText());
                    
                    JsonNode table = column.path("table");
                    if (!table.isMissingNode()) {
                        Map<String, Object> tableInfo = new HashMap<>();
                        tableInfo.put("id", table.path("id").asText());
                        tableInfo.put("name", table.path("name").asText());
                        tableInfo.put("fullName", table.path("fullName").asText());
                        colInfo.put("table", tableInfo);
                    }
                    columns.add(colInfo);
                }
                lineage.put("upstreamColumns", columns);
            }
            
            return objectMapper.writeValueAsString(lineage);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize lineage info: {}", e.getMessage());
            return null;
        }
    }

    private String extractCalculationLogic(JsonNode calculations) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode calc : calculations) {
            if (sb.length() > 0) {
                sb.append("\n---\n");
            }
            sb.append("Name: ").append(calc.path("name").asText()).append("\n");
            sb.append("Formula: ").append(calc.path("formula").asText());
        }
        return sb.toString();
    }
}
