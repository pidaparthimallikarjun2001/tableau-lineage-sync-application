package com.example.tableau.service;

import com.example.tableau.config.CollibraApiConfig;
import com.example.tableau.dto.collibra.*;
import com.example.tableau.entity.*;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for ingesting Tableau assets from the local database to Collibra.
 * Handles the mapping of Tableau entities to Collibra assets and manages
 * incremental synchronization based on status flags.
 */
@Service
public class CollibraIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CollibraIngestionService.class);

    private final CollibraRestClient collibraClient;
    private final CollibraApiConfig collibraConfig;
    private final TableauServerRepository serverRepository;
    private final TableauSiteRepository siteRepository;
    private final TableauProjectRepository projectRepository;
    private final TableauWorkbookRepository workbookRepository;
    private final TableauWorksheetRepository worksheetRepository;
    private final TableauDataSourceRepository dataSourceRepository;
    private final ReportAttributeRepository reportAttributeRepository;

    public CollibraIngestionService(
            CollibraRestClient collibraClient,
            CollibraApiConfig collibraConfig,
            TableauServerRepository serverRepository,
            TableauSiteRepository siteRepository,
            TableauProjectRepository projectRepository,
            TableauWorkbookRepository workbookRepository,
            TableauWorksheetRepository worksheetRepository,
            TableauDataSourceRepository dataSourceRepository,
            ReportAttributeRepository reportAttributeRepository) {
        this.collibraClient = collibraClient;
        this.collibraConfig = collibraConfig;
        this.serverRepository = serverRepository;
        this.siteRepository = siteRepository;
        this.projectRepository = projectRepository;
        this.workbookRepository = workbookRepository;
        this.worksheetRepository = worksheetRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.reportAttributeRepository = reportAttributeRepository;
    }

    /**
     * Check if Collibra integration is configured.
     */
    public boolean isConfigured() {
        return collibraClient.isConfigured();
    }

    /**
     * Test connection to Collibra.
     */
    public Mono<Boolean> testConnection() {
        return collibraClient.testConnection();
    }

    // ======================== Server Ingestion ========================

    /**
     * Ingest all servers to Collibra based on status flags.
     * First run: All assets ingested.
     * Subsequent runs: Only NEW, UPDATED, DELETED changes.
     */
    public Mono<CollibraIngestionResult> ingestServersToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauServer> servers = serverRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauServer> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauServer server : servers) {
            if (server.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(server);
            } else if (server.getStatusFlag() == StatusFlag.NEW || 
                       server.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapServerToCollibraAsset(server));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} servers to Collibra ({} to create/update, {} to delete, {} skipped)",
                servers.size(), assetsToIngest.size(), toDelete.size(), skipped);

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Server")
                .map(result -> {
                    result.setAssetsDeleted(toDelete.size());
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest a single server to Collibra.
     */
    public Mono<CollibraIngestionResult> ingestServerToCollibra(Long serverId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return serverRepository.findById(serverId)
                .map(server -> {
                    CollibraAsset asset = mapServerToCollibraAsset(server);
                    return collibraClient.importAssets(List.of(asset), "Server");
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Server", "Server not found: " + serverId)));
    }

    private CollibraAsset mapServerToCollibraAsset(TableauServer server) {
        String fullName = CollibraAsset.createServerFullName(server.getAssetId(), server.getName());
        
        List<CollibraAttribute> attributes = new ArrayList<>();
        addAttribute(attributes, "Description", "Tableau Server: " + server.getName());
        addAttribute(attributes, "URL", server.getServerUrl());
        addAttribute(attributes, "Version", server.getVersion());

        return CollibraAsset.builder()
                .externalEntityId(fullName)
                .fullName(fullName)
                .displayName(server.getName())
                .assetTypeName("Tableau Server")
                .domainName(collibraConfig.getServerDomainName())
                .communityName(collibraConfig.getCommunityName())
                .status("Approved")
                .attributes(attributes)
                .identifier(server.getAssetId())
                .build();
    }

    // ======================== Site Ingestion ========================

    /**
     * Ingest all sites to Collibra based on status flags.
     */
    public Mono<CollibraIngestionResult> ingestSitesToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauSite> sites = siteRepository.findAllWithServer();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauSite> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauSite site : sites) {
            if (site.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(site);
            } else if (site.getStatusFlag() == StatusFlag.NEW || 
                       site.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapSiteToCollibraAsset(site));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} sites to Collibra ({} to create/update, {} to delete, {} skipped)",
                sites.size(), assetsToIngest.size(), toDelete.size(), skipped);

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Site")
                .map(result -> {
                    result.setAssetsDeleted(toDelete.size());
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest a single site to Collibra.
     */
    public Mono<CollibraIngestionResult> ingestSiteToCollibra(Long siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return siteRepository.findByIdWithServer(siteId)
                .map(site -> {
                    CollibraAsset asset = mapSiteToCollibraAsset(site);
                    return collibraClient.importAssets(List.of(asset), "Site");
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Site", "Site not found: " + siteId)));
    }

    private CollibraAsset mapSiteToCollibraAsset(TableauSite site) {
        String fullName = CollibraAsset.createFullName(site.getAssetId(), site.getAssetId(), site.getName());
        
        List<CollibraAttribute> attributes = new ArrayList<>();
        addAttribute(attributes, "Description", "Tableau Site: " + site.getName());
        addAttribute(attributes, "Content URL", site.getContentUrl());
        addAttribute(attributes, "Site URL", site.getSiteUrl());

        // Add relation to parent server if available
        List<CollibraRelation> sourceRelations = new ArrayList<>();
        if (site.getServer() != null) {
            TableauServer server = site.getServer();
            String serverFullName = CollibraAsset.createServerFullName(server.getAssetId(), server.getName());
            sourceRelations.add(CollibraRelation.builder()
                    .relationTypeName("contains")
                    .sourceFullName(serverFullName)
                    .targetFullName(fullName)
                    .build());
        }

        return CollibraAsset.builder()
                .externalEntityId(fullName)
                .fullName(fullName)
                .displayName(site.getName())
                .assetTypeName("Tableau Site")
                .domainName(collibraConfig.getSiteDomainName())
                .communityName(collibraConfig.getCommunityName())
                .status("Approved")
                .attributes(attributes)
                .targetRelations(sourceRelations.isEmpty() ? null : sourceRelations)
                .identifier(site.getAssetId())
                .build();
    }

    // ======================== Project Ingestion ========================

    /**
     * Ingest all projects to Collibra based on status flags.
     * Handles nested/child projects and their relations.
     */
    public Mono<CollibraIngestionResult> ingestProjectsToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauProject> projects = projectRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauProject> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauProject project : projects) {
            if (project.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(project);
            } else if (project.getStatusFlag() == StatusFlag.NEW || 
                       project.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapProjectToCollibraAsset(project));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} projects to Collibra ({} to create/update, {} to delete, {} skipped)",
                projects.size(), assetsToIngest.size(), toDelete.size(), skipped);

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Project")
                .map(result -> {
                    result.setAssetsDeleted(toDelete.size());
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest a single project to Collibra.
     */
    public Mono<CollibraIngestionResult> ingestProjectToCollibra(Long projectId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return projectRepository.findById(projectId)
                .map(project -> {
                    CollibraAsset asset = mapProjectToCollibraAsset(project);
                    return collibraClient.importAssets(List.of(asset), "Project");
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Project", "Project not found: " + projectId)));
    }

    private CollibraAsset mapProjectToCollibraAsset(TableauProject project) {
        String fullName = CollibraAsset.createFullName(project.getSiteId(), project.getAssetId(), project.getName());
        
        List<CollibraAttribute> attributes = new ArrayList<>();
        addAttribute(attributes, "Description", project.getDescription());
        addAttribute(attributes, "Site ID", project.getSiteId());

        // Add relation to parent project if this is a nested project
        List<CollibraRelation> targetRelations = new ArrayList<>();
        if (project.getParentProjectId() != null && !project.getParentProjectId().isEmpty()) {
            // Find parent project to get its name
            projectRepository.findByAssetIdAndSiteId(project.getParentProjectId(), project.getSiteId())
                    .ifPresent(parentProject -> {
                        String parentFullName = CollibraAsset.createFullName(
                                parentProject.getSiteId(), parentProject.getAssetId(), parentProject.getName());
                        targetRelations.add(CollibraRelation.builder()
                                .relationTypeName("contains")
                                .sourceFullName(parentFullName)
                                .targetFullName(fullName)
                                .build());
                    });
        }

        // Add relation to parent site
        if (project.getSite() != null) {
            TableauSite site = project.getSite();
            String siteFullName = CollibraAsset.createFullName(site.getAssetId(), site.getAssetId(), site.getName());
            targetRelations.add(CollibraRelation.builder()
                    .relationTypeName("contains")
                    .sourceFullName(siteFullName)
                    .targetFullName(fullName)
                    .build());
        }

        return CollibraAsset.builder()
                .externalEntityId(fullName)
                .fullName(fullName)
                .displayName(project.getName())
                .assetTypeName("Tableau Project")
                .domainName(collibraConfig.getProjectDomainName())
                .communityName(collibraConfig.getCommunityName())
                .status("Approved")
                .attributes(attributes)
                .targetRelations(targetRelations.isEmpty() ? null : targetRelations)
                .identifier(project.getAssetId())
                .build();
    }

    // ======================== Workbook Ingestion ========================

    /**
     * Ingest all workbooks to Collibra based on status flags.
     */
    public Mono<CollibraIngestionResult> ingestWorkbooksToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorkbook> workbooks = workbookRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauWorkbook> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauWorkbook workbook : workbooks) {
            if (workbook.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(workbook);
            } else if (workbook.getStatusFlag() == StatusFlag.NEW || 
                       workbook.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapWorkbookToCollibraAsset(workbook));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} workbooks to Collibra ({} to create/update, {} to delete, {} skipped)",
                workbooks.size(), assetsToIngest.size(), toDelete.size(), skipped);

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Workbook")
                .map(result -> {
                    result.setAssetsDeleted(toDelete.size());
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest a single workbook to Collibra.
     */
    public Mono<CollibraIngestionResult> ingestWorkbookToCollibra(Long workbookId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return workbookRepository.findById(workbookId)
                .map(workbook -> {
                    CollibraAsset asset = mapWorkbookToCollibraAsset(workbook);
                    return collibraClient.importAssets(List.of(asset), "Workbook");
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Workbook", "Workbook not found: " + workbookId)));
    }

    private CollibraAsset mapWorkbookToCollibraAsset(TableauWorkbook workbook) {
        String fullName = CollibraAsset.createFullName(workbook.getSiteId(), workbook.getAssetId(), workbook.getName());
        
        List<CollibraAttribute> attributes = new ArrayList<>();
        addAttribute(attributes, "Description", workbook.getDescription());
        addAttribute(attributes, "Owner", workbook.getOwner());
        addAttribute(attributes, "Content URL", workbook.getContentUrl());
        addAttribute(attributes, "Site ID", workbook.getSiteId());

        // Add relation to parent project
        List<CollibraRelation> targetRelations = new ArrayList<>();
        if (workbook.getProject() != null) {
            TableauProject project = workbook.getProject();
            String projectFullName = CollibraAsset.createFullName(
                    project.getSiteId(), project.getAssetId(), project.getName());
            targetRelations.add(CollibraRelation.builder()
                    .relationTypeName("contains")
                    .sourceFullName(projectFullName)
                    .targetFullName(fullName)
                    .build());
        }

        return CollibraAsset.builder()
                .externalEntityId(fullName)
                .fullName(fullName)
                .displayName(workbook.getName())
                .assetTypeName("Tableau Workbook")
                .domainName(collibraConfig.getWorkbookDomainName())
                .communityName(collibraConfig.getCommunityName())
                .status("Approved")
                .attributes(attributes)
                .targetRelations(targetRelations.isEmpty() ? null : targetRelations)
                .identifier(workbook.getAssetId())
                .build();
    }

    // ======================== Worksheet Ingestion ========================

    /**
     * Ingest all worksheets to Collibra based on status flags.
     */
    public Mono<CollibraIngestionResult> ingestWorksheetsToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorksheet> worksheets = worksheetRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauWorksheet> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauWorksheet worksheet : worksheets) {
            if (worksheet.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(worksheet);
            } else if (worksheet.getStatusFlag() == StatusFlag.NEW || 
                       worksheet.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapWorksheetToCollibraAsset(worksheet));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} worksheets to Collibra ({} to create/update, {} to delete, {} skipped)",
                worksheets.size(), assetsToIngest.size(), toDelete.size(), skipped);

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Worksheet")
                .map(result -> {
                    result.setAssetsDeleted(toDelete.size());
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest a single worksheet to Collibra.
     */
    public Mono<CollibraIngestionResult> ingestWorksheetToCollibra(Long worksheetId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return worksheetRepository.findById(worksheetId)
                .map(worksheet -> {
                    CollibraAsset asset = mapWorksheetToCollibraAsset(worksheet);
                    return collibraClient.importAssets(List.of(asset), "Worksheet");
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Worksheet", "Worksheet not found: " + worksheetId)));
    }

    private CollibraAsset mapWorksheetToCollibraAsset(TableauWorksheet worksheet) {
        String fullName = CollibraAsset.createFullName(worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName());
        
        List<CollibraAttribute> attributes = new ArrayList<>();
        addAttribute(attributes, "Owner", worksheet.getOwner());
        addAttribute(attributes, "Site ID", worksheet.getSiteId());

        // Add relation to parent workbook
        List<CollibraRelation> targetRelations = new ArrayList<>();
        if (worksheet.getWorkbook() != null) {
            TableauWorkbook workbook = worksheet.getWorkbook();
            String workbookFullName = CollibraAsset.createFullName(
                    workbook.getSiteId(), workbook.getAssetId(), workbook.getName());
            targetRelations.add(CollibraRelation.builder()
                    .relationTypeName("contains")
                    .sourceFullName(workbookFullName)
                    .targetFullName(fullName)
                    .build());
        }

        return CollibraAsset.builder()
                .externalEntityId(fullName)
                .fullName(fullName)
                .displayName(worksheet.getName())
                .assetTypeName("Tableau Worksheet")
                .domainName(collibraConfig.getWorksheetDomainName())
                .communityName(collibraConfig.getCommunityName())
                .status("Approved")
                .attributes(attributes)
                .targetRelations(targetRelations.isEmpty() ? null : targetRelations)
                .identifier(worksheet.getAssetId())
                .build();
    }

    // ======================== DataSource Ingestion ========================

    /**
     * Ingest all data sources to Collibra based on status flags.
     */
    public Mono<CollibraIngestionResult> ingestDataSourcesToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauDataSource> dataSources = dataSourceRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauDataSource> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauDataSource dataSource : dataSources) {
            if (dataSource.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(dataSource);
            } else if (dataSource.getStatusFlag() == StatusFlag.NEW || 
                       dataSource.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapDataSourceToCollibraAsset(dataSource));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} data sources to Collibra ({} to create/update, {} to delete, {} skipped)",
                dataSources.size(), assetsToIngest.size(), toDelete.size(), skipped);

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "DataSource")
                .map(result -> {
                    result.setAssetsDeleted(toDelete.size());
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest a single data source to Collibra.
     */
    public Mono<CollibraIngestionResult> ingestDataSourceToCollibra(Long dataSourceId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return dataSourceRepository.findById(dataSourceId)
                .map(dataSource -> {
                    CollibraAsset asset = mapDataSourceToCollibraAsset(dataSource);
                    return collibraClient.importAssets(List.of(asset), "DataSource");
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("DataSource", "DataSource not found: " + dataSourceId)));
    }

    private CollibraAsset mapDataSourceToCollibraAsset(TableauDataSource dataSource) {
        String fullName = CollibraAsset.createFullName(dataSource.getSiteId(), dataSource.getAssetId(), dataSource.getName());
        
        List<CollibraAttribute> attributes = new ArrayList<>();
        addAttribute(attributes, "Description", dataSource.getDescription());
        addAttribute(attributes, "Owner", dataSource.getOwner());
        addAttribute(attributes, "Connection Type", dataSource.getConnectionType());
        addAttribute(attributes, "Table Name", dataSource.getTableName());
        addAttribute(attributes, "Schema Name", dataSource.getSchemaName());
        addAttribute(attributes, "Database Name", dataSource.getDatabaseName());
        addAttribute(attributes, "Server Name", dataSource.getServerName());
        addAttribute(attributes, "Is Certified", dataSource.getIsCertified() != null ? dataSource.getIsCertified().toString() : null);
        addAttribute(attributes, "Is Published", dataSource.getIsPublished() != null ? dataSource.getIsPublished().toString() : null);
        addAttribute(attributes, "Source Type", dataSource.getSourceType() != null ? dataSource.getSourceType().name() : null);
        addAttribute(attributes, "Site ID", dataSource.getSiteId());

        // Add relation to parent workbook if embedded
        List<CollibraRelation> targetRelations = new ArrayList<>();
        if (dataSource.getWorkbook() != null) {
            TableauWorkbook workbook = dataSource.getWorkbook();
            String workbookFullName = CollibraAsset.createFullName(
                    workbook.getSiteId(), workbook.getAssetId(), workbook.getName());
            targetRelations.add(CollibraRelation.builder()
                    .relationTypeName("uses")
                    .sourceFullName(workbookFullName)
                    .targetFullName(fullName)
                    .build());
        }

        return CollibraAsset.builder()
                .externalEntityId(fullName)
                .fullName(fullName)
                .displayName(dataSource.getName())
                .assetTypeName("Tableau Data Source")
                .domainName(collibraConfig.getDatasourceDomainName())
                .communityName(collibraConfig.getCommunityName())
                .status("Approved")
                .attributes(attributes)
                .targetRelations(targetRelations.isEmpty() ? null : targetRelations)
                .identifier(dataSource.getAssetId())
                .build();
    }

    // ======================== Report Attribute Ingestion ========================

    /**
     * Ingest all report attributes to Collibra based on status flags.
     */
    public Mono<CollibraIngestionResult> ingestReportAttributesToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<ReportAttribute> reportAttributes = reportAttributeRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<ReportAttribute> toDelete = new ArrayList<>();
        int skipped = 0;

        for (ReportAttribute attr : reportAttributes) {
            if (attr.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(attr);
            } else if (attr.getStatusFlag() == StatusFlag.NEW || 
                       attr.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapReportAttributeToCollibraAsset(attr));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} report attributes to Collibra ({} to create/update, {} to delete, {} skipped)",
                reportAttributes.size(), assetsToIngest.size(), toDelete.size(), skipped);

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "ReportAttribute")
                .map(result -> {
                    result.setAssetsDeleted(toDelete.size());
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest a single report attribute to Collibra.
     */
    public Mono<CollibraIngestionResult> ingestReportAttributeToCollibra(Long reportAttributeId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return reportAttributeRepository.findById(reportAttributeId)
                .map(attr -> {
                    CollibraAsset asset = mapReportAttributeToCollibraAsset(attr);
                    return collibraClient.importAssets(List.of(asset), "ReportAttribute");
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("ReportAttribute", "ReportAttribute not found: " + reportAttributeId)));
    }

    private CollibraAsset mapReportAttributeToCollibraAsset(ReportAttribute attr) {
        String fullName = CollibraAsset.createFullName(attr.getSiteId(), attr.getAssetId(), attr.getName());
        
        List<CollibraAttribute> attributes = new ArrayList<>();
        addAttribute(attributes, "Data Type", attr.getDataType());
        addAttribute(attributes, "Field Role", attr.getFieldRole());
        addAttribute(attributes, "Is Calculated", attr.getIsCalculated() != null ? attr.getIsCalculated().toString() : null);
        addAttribute(attributes, "Calculation Logic", attr.getCalculationLogic());
        addAttribute(attributes, "Source DataSource ID", attr.getSourceDatasourceId());
        addAttribute(attributes, "Source DataSource Name", attr.getSourceDatasourceName());
        addAttribute(attributes, "Source Column Name", attr.getSourceColumnName());
        addAttribute(attributes, "Source Table Name", attr.getSourceTableName());
        addAttribute(attributes, "Site ID", attr.getSiteId());
        addAttribute(attributes, "Worksheet ID", attr.getWorksheetId());

        // Add relation to parent worksheet
        List<CollibraRelation> targetRelations = new ArrayList<>();
        if (attr.getWorksheet() != null) {
            TableauWorksheet worksheet = attr.getWorksheet();
            String worksheetFullName = CollibraAsset.createFullName(
                    worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName());
            targetRelations.add(CollibraRelation.builder()
                    .relationTypeName("contains")
                    .sourceFullName(worksheetFullName)
                    .targetFullName(fullName)
                    .build());
        }

        // Add relation to source data source
        if (attr.getDataSource() != null) {
            TableauDataSource dataSource = attr.getDataSource();
            String dataSourceFullName = CollibraAsset.createFullName(
                    dataSource.getSiteId(), dataSource.getAssetId(), dataSource.getName());
            targetRelations.add(CollibraRelation.builder()
                    .relationTypeName("sources from")
                    .sourceFullName(fullName)
                    .targetFullName(dataSourceFullName)
                    .build());
        }

        return CollibraAsset.builder()
                .externalEntityId(fullName)
                .fullName(fullName)
                .displayName(attr.getName())
                .assetTypeName("Tableau Report Attribute")
                .domainName(collibraConfig.getReportAttributeDomainName())
                .communityName(collibraConfig.getCommunityName())
                .status("Approved")
                .attributes(attributes)
                .targetRelations(targetRelations.isEmpty() ? null : targetRelations)
                .identifier(attr.getAssetId())
                .build();
    }

    // ======================== Bulk Ingestion ========================

    /**
     * Ingest all asset types to Collibra in the correct order.
     */
    public Mono<CollibraIngestionResult> ingestAllToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        log.info("Starting full ingestion of all assets to Collibra");

        return ingestServersToCollibra()
                .flatMap(serverResult -> {
                    log.info("Server ingestion complete: {}", serverResult);
                    return ingestSitesToCollibra();
                })
                .flatMap(siteResult -> {
                    log.info("Site ingestion complete: {}", siteResult);
                    return ingestProjectsToCollibra();
                })
                .flatMap(projectResult -> {
                    log.info("Project ingestion complete: {}", projectResult);
                    return ingestWorkbooksToCollibra();
                })
                .flatMap(workbookResult -> {
                    log.info("Workbook ingestion complete: {}", workbookResult);
                    return ingestWorksheetsToCollibra();
                })
                .flatMap(worksheetResult -> {
                    log.info("Worksheet ingestion complete: {}", worksheetResult);
                    return ingestDataSourcesToCollibra();
                })
                .flatMap(dataSourceResult -> {
                    log.info("DataSource ingestion complete: {}", dataSourceResult);
                    return ingestReportAttributesToCollibra();
                })
                .map(reportAttrResult -> {
                    log.info("ReportAttribute ingestion complete: {}", reportAttrResult);
                    return CollibraIngestionResult.builder()
                            .assetType("All")
                            .success(true)
                            .message("Full ingestion to Collibra completed successfully")
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Full ingestion failed: {}", e.getMessage(), e);
                    return Mono.just(CollibraIngestionResult.failure("All", "Full ingestion failed: " + e.getMessage()));
                });
    }

    // ======================== Helper Methods ========================

    private void addAttribute(List<CollibraAttribute> attributes, String typeName, String value) {
        if (value != null && !value.trim().isEmpty()) {
            attributes.add(CollibraAttribute.builder()
                    .attributeTypeName(typeName)
                    .value(value)
                    .build());
        }
    }
}
