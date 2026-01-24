package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauDataSource;
import com.example.tableau.entity.TableauWorkbook;
import com.example.tableau.enums.SourceType;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.repository.TableauDataSourceRepository;
import com.example.tableau.repository.TableauWorkbookRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Service for managing Tableau Data Source entities.
 * Handles both published and embedded data sources, including custom SQL.
 */
@Service
public class DataSourceService extends BaseAssetService {

    private final TableauDataSourceRepository dataSourceRepository;
    private final TableauWorkbookRepository workbookRepository;
    private final TableauGraphQLClient graphQLClient;
    private final TableauAuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataSourceService(TableauDataSourceRepository dataSourceRepository,
                             TableauWorkbookRepository workbookRepository,
                             TableauGraphQLClient graphQLClient,
                             TableauAuthService authService) {
        this.dataSourceRepository = dataSourceRepository;
        this.workbookRepository = workbookRepository;
        this.graphQLClient = graphQLClient;
        this.authService = authService;
    }

    /**
     * Get all active data sources from the database.
     */
    public List<TableauDataSource> getAllActiveDataSources() {
        return dataSourceRepository.findAllActive();
    }

    /**
     * Get all active data sources for a site.
     */
    public List<TableauDataSource> getActiveDataSourcesBySiteId(String siteId) {
        return dataSourceRepository.findAllActiveBySiteId(siteId);
    }

    /**
     * Get data sources by source type.
     */
    public List<TableauDataSource> getDataSourcesByType(SourceType sourceType) {
        return dataSourceRepository.findBySourceType(sourceType);
    }

    /**
     * Get published data sources.
     */
    public List<TableauDataSource> getPublishedDataSources() {
        return dataSourceRepository.findByIsPublishedTrue();
    }

    /**
     * Get certified data sources.
     */
    public List<TableauDataSource> getCertifiedDataSources() {
        return dataSourceRepository.findByIsCertifiedTrue();
    }

    /**
     * Get data source by ID.
     */
    public TableauDataSource getDataSourceById(Long id) {
        return dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", id.toString()));
    }

    /**
     * Get data source by asset ID.
     */
    public Optional<TableauDataSource> getDataSourceByAssetId(String assetId) {
        return dataSourceRepository.findByAssetId(assetId);
    }

    /**
     * Fetch published data sources from Tableau GraphQL API.
     */
    public Mono<List<JsonNode>> fetchPublishedDataSourcesFromTableau() {
        return graphQLClient.fetchPublishedDatasources(100);
    }

    /**
     * Fetch embedded data sources from Tableau GraphQL API.
     */
    public Mono<List<JsonNode>> fetchEmbeddedDataSourcesFromTableau() {
        return graphQLClient.fetchEmbeddedDatasources(100);
    }

    /**
     * Fetch custom SQL tables from Tableau GraphQL API.
     */
    public Mono<List<JsonNode>> fetchCustomSQLTablesFromTableau() {
        return graphQLClient.fetchCustomSQLTables(100);
    }

    /**
     * Ingest or update all data sources from Tableau (published, embedded, and custom SQL).
     */
    @Transactional
    public Mono<IngestionResult> ingestDataSources() {
        String currentSiteId = authService.getCurrentSiteId();
        if (currentSiteId == null) {
            return Mono.just(createFailureResult("DataSource", "No active site. Please authenticate first."));
        }
        
        // Combine results from all data source types
        return Mono.zip(
                graphQLClient.fetchPublishedDatasources(100),
                graphQLClient.fetchEmbeddedDatasources(100),
                graphQLClient.fetchCustomSQLTables(100)
        ).map(tuple -> {
            List<JsonNode> publishedDs = tuple.getT1();
            List<JsonNode> embeddedDs = tuple.getT2();
            List<JsonNode> customSqlTables = tuple.getT3();
            
            try {
                int newCount = 0, updatedCount = 0, deletedCount = 0, unchangedCount = 0;
                Set<String> processedAssetIds = new HashSet<>();
                
                // Process published data sources
                for (JsonNode dsNode : publishedDs) {
                    int[] result = processDataSource(dsNode, currentSiteId, SourceType.PUBLISHED, true, processedAssetIds);
                    newCount += result[0];
                    updatedCount += result[1];
                    unchangedCount += result[2];
                }
                
                // Process embedded data sources
                for (JsonNode dsNode : embeddedDs) {
                    int[] result = processEmbeddedDataSource(dsNode, currentSiteId, processedAssetIds);
                    newCount += result[0];
                    updatedCount += result[1];
                    unchangedCount += result[2];
                }
                
                // Process custom SQL tables - create data source entries for them
                for (JsonNode sqlTable : customSqlTables) {
                    int[] result = processCustomSQLTable(sqlTable, currentSiteId, processedAssetIds);
                    newCount += result[0];
                    updatedCount += result[1];
                    unchangedCount += result[2];
                }
                
                // Mark data sources not in Tableau as deleted
                List<TableauDataSource> existingDs = dataSourceRepository.findAllActiveBySiteId(currentSiteId);
                for (TableauDataSource ds : existingDs) {
                    if (!processedAssetIds.contains(ds.getAssetId())) {
                        ds.setStatusFlag(StatusFlag.DELETED);
                        dataSourceRepository.save(ds);
                        deletedCount++;
                        log.info("Soft deleted data source: {}", ds.getName());
                    }
                }
                
                int total = newCount + updatedCount + unchangedCount;
                return createSuccessResult("DataSource", total, newCount, updatedCount, deletedCount, unchangedCount);
            } catch (Exception e) {
                log.error("Error ingesting data sources: {}", e.getMessage(), e);
                return createFailureResult("DataSource", e.getMessage());
            }
        }).onErrorResume(e -> {
            log.error("Failed to ingest data sources: {}", e.getMessage(), e);
            return Mono.just(createFailureResult("DataSource", e.getMessage()));
        });
    }

    private int[] processDataSource(JsonNode dsNode, String siteId, SourceType sourceType, 
                                     boolean isPublished, Set<String> processedAssetIds) {
        int newCount = 0, updatedCount = 0, unchangedCount = 0;
        
        String assetId = dsNode.path("luid").asText(dsNode.path("id").asText());
        String name = dsNode.path("name").asText();
        String description = dsNode.path("description").asText(null);
        boolean isCertified = dsNode.path("isCertified").asBoolean(false);
        
        // Owner info
        JsonNode ownerNode = dsNode.path("owner");
        String owner = !ownerNode.isMissingNode() ? ownerNode.path("username").asText(ownerNode.path("name").asText(null)) : null;
        String ownerId = !ownerNode.isMissingNode() ? ownerNode.path("id").asText(null) : null;
        
        // Extract upstream tables info
        String upstreamTables = extractUpstreamTables(dsNode.path("upstreamTables"));
        String connectionType = null;
        String tableName = null;
        String schemaName = null;
        String databaseName = null;
        String serverName = null;
        
        JsonNode tables = dsNode.path("upstreamTables");
        if (tables.isArray() && !tables.isEmpty()) {
            JsonNode firstTable = tables.get(0);
            tableName = firstTable.path("fullName").asText(firstTable.path("name").asText(null));
            schemaName = firstTable.path("schema").asText(null);
            connectionType = firstTable.path("connectionType").asText(null);
            
            JsonNode database = firstTable.path("database");
            if (!database.isMissingNode()) {
                databaseName = database.path("name").asText(null);
                if (connectionType == null) {
                    connectionType = database.path("connectionType").asText(null);
                }
            }
        }
        
        // Extract calculated fields
        String calculatedFields = extractCalculatedFields(dsNode.path("fields"));
        
        processedAssetIds.add(assetId);
        
        String newHash = generateMetadataHash(assetId, name, description, String.valueOf(isCertified),
                owner, connectionType, tableName, upstreamTables, siteId);
        
        Optional<TableauDataSource> existingDs = dataSourceRepository.findByAssetIdAndSiteId(assetId, siteId);
        
        if (existingDs.isPresent()) {
            TableauDataSource ds = existingDs.get();
            String existingHash = ds.getMetadataHash();
            StatusFlag newStatus = determineStatusFlag(existingHash, newHash, ds.getStatusFlag());
            
            if (newStatus == StatusFlag.UPDATED) {
                updateDataSource(ds, name, description, owner, ownerId, sourceType, isPublished,
                        isCertified, connectionType, tableName, schemaName, databaseName, 
                        serverName, null, upstreamTables, calculatedFields, newHash);
                updatedCount++;
                log.info("Updated data source: {}", name);
            } else {
                if (ds.getStatusFlag() != StatusFlag.ACTIVE && ds.getStatusFlag() != StatusFlag.DELETED) {
                    ds.setStatusFlag(StatusFlag.ACTIVE);
                    dataSourceRepository.save(ds);
                }
                unchangedCount++;
            }
        } else {
            TableauDataSource ds = createDataSource(assetId, siteId, name, description, owner, ownerId,
                    sourceType, isPublished, isCertified, connectionType, tableName, schemaName, 
                    databaseName, serverName, null, upstreamTables, calculatedFields, newHash, null);
            newCount++;
            log.info("Created new data source: {}", name);
        }
        
        return new int[]{newCount, updatedCount, unchangedCount};
    }

    private int[] processEmbeddedDataSource(JsonNode dsNode, String siteId, Set<String> processedAssetIds) {
        int newCount = 0, updatedCount = 0, unchangedCount = 0;
        
        String assetId = dsNode.path("id").asText();
        String name = dsNode.path("name").asText();
        
        // Workbook info
        JsonNode workbookNode = dsNode.path("workbook");
        String workbookLuid = !workbookNode.isMissingNode() ? 
                workbookNode.path("luid").asText(workbookNode.path("id").asText(null)) : null;
        
        // Determine source type
        SourceType sourceType = SourceType.DIRECT_IMPORT;
        JsonNode upstreamDatasources = dsNode.path("upstreamDatasources");
        if (upstreamDatasources.isArray() && !upstreamDatasources.isEmpty()) {
            sourceType = SourceType.PUBLISHED; // References published data source
        }
        
        // Extract upstream tables info
        String upstreamTables = extractUpstreamTables(dsNode.path("upstreamTables"));
        String connectionType = null;
        String tableName = null;
        String schemaName = null;
        String databaseName = null;
        
        JsonNode tables = dsNode.path("upstreamTables");
        if (tables.isArray() && !tables.isEmpty()) {
            JsonNode firstTable = tables.get(0);
            tableName = firstTable.path("fullName").asText(firstTable.path("name").asText(null));
            schemaName = firstTable.path("schema").asText(null);
            connectionType = firstTable.path("connectionType").asText(null);
            
            JsonNode database = firstTable.path("database");
            if (!database.isMissingNode()) {
                databaseName = database.path("name").asText(null);
            }
        }
        
        // Extract calculated fields
        String calculatedFields = extractCalculatedFields(dsNode.path("fields"));
        
        processedAssetIds.add(assetId);
        
        String newHash = generateMetadataHash(assetId, name, workbookLuid, connectionType, 
                tableName, upstreamTables, siteId);
        
        // Find workbook
        TableauWorkbook workbook = workbookLuid != null ? 
                workbookRepository.findByAssetIdAndSiteId(workbookLuid, siteId).orElse(null) : null;
        
        Optional<TableauDataSource> existingDs = dataSourceRepository.findByAssetIdAndSiteId(assetId, siteId);
        
        if (existingDs.isPresent()) {
            TableauDataSource ds = existingDs.get();
            String existingHash = ds.getMetadataHash();
            StatusFlag newStatus = determineStatusFlag(existingHash, newHash, ds.getStatusFlag());
            
            if (newStatus == StatusFlag.UPDATED) {
                updateDataSource(ds, name, null, null, null, sourceType, false,
                        false, connectionType, tableName, schemaName, databaseName, 
                        null, null, upstreamTables, calculatedFields, newHash);
                ds.setWorkbook(workbook);
                dataSourceRepository.save(ds);
                updatedCount++;
                log.info("Updated embedded data source: {}", name);
            } else {
                if (ds.getStatusFlag() != StatusFlag.ACTIVE && ds.getStatusFlag() != StatusFlag.DELETED) {
                    ds.setStatusFlag(StatusFlag.ACTIVE);
                    dataSourceRepository.save(ds);
                }
                unchangedCount++;
            }
        } else {
            TableauDataSource ds = createDataSource(assetId, siteId, name, null, null, null,
                    sourceType, false, false, connectionType, tableName, schemaName, 
                    databaseName, null, null, upstreamTables, calculatedFields, newHash, workbook);
            newCount++;
            log.info("Created new embedded data source: {}", name);
        }
        
        return new int[]{newCount, updatedCount, unchangedCount};
    }

    private int[] processCustomSQLTable(JsonNode sqlTable, String siteId, Set<String> processedAssetIds) {
        int newCount = 0, updatedCount = 0, unchangedCount = 0;
        
        String assetId = CUSTOM_SQL_PREFIX + sqlTable.path("id").asText();
        String name = sqlTable.path("name").asText();
        String customSqlQuery = sqlTable.path("query").asText(null);
        String connectionType = sqlTable.path("connectionType").asText(null);
        
        // Database info
        JsonNode database = sqlTable.path("database");
        String databaseName = !database.isMissingNode() ? database.path("name").asText(null) : null;
        if (connectionType == null && !database.isMissingNode()) {
            connectionType = database.path("connectionType").asText(null);
        }
        
        processedAssetIds.add(assetId);
        
        String newHash = generateMetadataHash(assetId, name, customSqlQuery, connectionType, 
                databaseName, siteId);
        
        Optional<TableauDataSource> existingDs = dataSourceRepository.findByAssetIdAndSiteId(assetId, siteId);
        
        if (existingDs.isPresent()) {
            TableauDataSource ds = existingDs.get();
            String existingHash = ds.getMetadataHash();
            StatusFlag newStatus = determineStatusFlag(existingHash, newHash, ds.getStatusFlag());
            
            if (newStatus == StatusFlag.UPDATED) {
                ds.setName(name);
                ds.setSourceType(SourceType.CUSTOM_SQL);
                ds.setCustomSqlQuery(customSqlQuery);
                ds.setConnectionType(connectionType);
                ds.setDatabaseName(databaseName);
                ds.setMetadataHash(newHash);
                ds.setStatusFlag(StatusFlag.UPDATED);
                dataSourceRepository.save(ds);
                updatedCount++;
                log.info("Updated custom SQL table: {}", name);
            } else {
                if (ds.getStatusFlag() != StatusFlag.ACTIVE && ds.getStatusFlag() != StatusFlag.DELETED) {
                    ds.setStatusFlag(StatusFlag.ACTIVE);
                    dataSourceRepository.save(ds);
                }
                unchangedCount++;
            }
        } else {
            TableauDataSource ds = TableauDataSource.builder()
                    .assetId(assetId)
                    .siteId(siteId)
                    .name(name)
                    .sourceType(SourceType.CUSTOM_SQL)
                    .customSqlQuery(customSqlQuery)
                    .connectionType(connectionType)
                    .databaseName(databaseName)
                    .metadataHash(newHash)
                    .statusFlag(StatusFlag.NEW)
                    .build();
            dataSourceRepository.save(ds);
            newCount++;
            log.info("Created new custom SQL table entry: {}", name);
        }
        
        return new int[]{newCount, updatedCount, unchangedCount};
    }

    private void updateDataSource(TableauDataSource ds, String name, String description, 
                                   String owner, String ownerId, SourceType sourceType, 
                                   boolean isPublished, boolean isCertified,
                                   String connectionType, String tableName, String schemaName,
                                   String databaseName, String serverName, String customSqlQuery,
                                   String upstreamTables, String calculatedFields, String newHash) {
        ds.setName(name);
        ds.setDescription(description);
        ds.setOwner(owner);
        ds.setOwnerId(ownerId);
        ds.setSourceType(sourceType);
        ds.setIsPublished(isPublished);
        ds.setIsCertified(isCertified);
        ds.setConnectionType(connectionType);
        ds.setTableName(tableName);
        ds.setSchemaName(schemaName);
        ds.setDatabaseName(databaseName);
        ds.setServerName(serverName);
        ds.setCustomSqlQuery(customSqlQuery);
        ds.setUpstreamTables(upstreamTables);
        ds.setCalculatedFields(calculatedFields);
        ds.setMetadataHash(newHash);
        ds.setStatusFlag(StatusFlag.UPDATED);
        dataSourceRepository.save(ds);
    }

    private TableauDataSource createDataSource(String assetId, String siteId, String name, 
                                                String description, String owner, String ownerId,
                                                SourceType sourceType, boolean isPublished, 
                                                boolean isCertified, String connectionType,
                                                String tableName, String schemaName, String databaseName,
                                                String serverName, String customSqlQuery,
                                                String upstreamTables, String calculatedFields,
                                                String newHash, TableauWorkbook workbook) {
        TableauDataSource ds = TableauDataSource.builder()
                .assetId(assetId)
                .siteId(siteId)
                .name(name)
                .description(description)
                .owner(owner)
                .ownerId(ownerId)
                .sourceType(sourceType)
                .isPublished(isPublished)
                .isCertified(isCertified)
                .connectionType(connectionType)
                .tableName(tableName)
                .schemaName(schemaName)
                .databaseName(databaseName)
                .serverName(serverName)
                .customSqlQuery(customSqlQuery)
                .upstreamTables(upstreamTables)
                .calculatedFields(calculatedFields)
                .metadataHash(newHash)
                .statusFlag(StatusFlag.NEW)
                .workbook(workbook)
                .build();
        return dataSourceRepository.save(ds);
    }

    /**
     * Soft delete all data sources for a workbook.
     */
    @Transactional
    public void softDeleteDataSourcesForWorkbook(Long workbookId) {
        List<TableauDataSource> dataSources = dataSourceRepository.findByWorkbookDbId(workbookId);
        for (TableauDataSource ds : dataSources) {
            ds.setStatusFlag(StatusFlag.DELETED);
            dataSourceRepository.save(ds);
        }
    }

    private String extractUpstreamTables(JsonNode tables) {
        if (tables == null || !tables.isArray() || tables.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> tableList = new ArrayList<>();
            for (JsonNode table : tables) {
                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("id", table.path("id").asText());
                tableInfo.put("name", table.path("name").asText());
                tableInfo.put("fullName", table.path("fullName").asText());
                tableInfo.put("schema", table.path("schema").asText());
                tableInfo.put("connectionType", table.path("connectionType").asText());
                
                JsonNode database = table.path("database");
                if (!database.isMissingNode()) {
                    Map<String, Object> dbInfo = new HashMap<>();
                    dbInfo.put("id", database.path("id").asText());
                    dbInfo.put("name", database.path("name").asText());
                    dbInfo.put("connectionType", database.path("connectionType").asText());
                    tableInfo.put("database", dbInfo);
                }
                tableList.add(tableInfo);
            }
            return objectMapper.writeValueAsString(tableList);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize upstream tables: {}", e.getMessage());
            return null;
        }
    }

    private String extractCalculatedFields(JsonNode fields) {
        if (fields == null || !fields.isArray()) {
            return null;
        }
        try {
            List<Map<String, Object>> calcFields = new ArrayList<>();
            for (JsonNode field : fields) {
                // Check if field is calculated based on __typename
                String fieldType = field.path("__typename").asText("");
                if ("CalculatedField".equals(fieldType)) {
                    Map<String, Object> fieldInfo = new HashMap<>();
                    fieldInfo.put("id", field.path("id").asText());
                    fieldInfo.put("name", field.path("name").asText());
                    fieldInfo.put("formula", field.path("formula").asText());
                    fieldInfo.put("dataType", field.path("dataType").asText());
                    calcFields.add(fieldInfo);
                }
            }
            if (calcFields.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(calcFields);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize calculated fields: {}", e.getMessage());
            return null;
        }
    }
}
