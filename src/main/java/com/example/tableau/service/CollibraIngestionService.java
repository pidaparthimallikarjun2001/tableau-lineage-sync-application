package com.example.tableau.service;

import com.example.tableau.config.CollibraApiConfig;
import com.example.tableau.dto.collibra.*;
import com.example.tableau.entity.*;
import com.example.tableau.enums.CollibraSyncStatus;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // ======================== Helper Classes ========================

    /**
     * Helper class to store deletion information for deferred deletion.
     * Used when ingesting all assets to defer deletions until after all imports complete.
     */
    private static class DeletionInfo {
        final List<String> identifierNames;
        final String domainName;
        final String communityName;

        DeletionInfo(List<String> identifierNames, String domainName, String communityName) {
            this.identifierNames = identifierNames;
            this.domainName = domainName;
            this.communityName = communityName;
        }
    }

    /**
     * Perform all accumulated deletions.
     * Returns the total count of successfully deleted assets.
     */
    private Mono<Integer> performAllDeletions(List<DeletionInfo> deletions) {
        if (deletions.isEmpty()) {
            return Mono.just(0);
        }

        log.info("Starting deletion of {} asset groups", deletions.size());
        Mono<Integer> result = Mono.just(0);
        
        for (DeletionInfo deletion : deletions) {
            result = result.flatMap(count -> 
                deleteAssetsFromCollibra(deletion.identifierNames, deletion.domainName, deletion.communityName)
                    .map(deletedCount -> count + deletedCount)
            );
        }
        
        return result;
    }

    // ======================== Collibra Sync Status Update Methods ========================

    /**
     * Update the Collibra sync status for a list of servers after successful ingestion.
     */
    @Transactional
    public void updateServerCollibraSyncStatus(List<TableauServer> servers, CollibraSyncStatus status) {
        for (TableauServer server : servers) {
            server.setCollibraSyncStatus(status);
        }
        serverRepository.saveAll(servers);
        log.debug("Updated collibraSyncStatus to {} for {} servers", status, servers.size());
    }

    /**
     * Update the Collibra sync status for a list of sites after successful ingestion.
     */
    @Transactional
    public void updateSiteCollibraSyncStatus(List<TableauSite> sites, CollibraSyncStatus status) {
        for (TableauSite site : sites) {
            site.setCollibraSyncStatus(status);
        }
        siteRepository.saveAll(sites);
        log.debug("Updated collibraSyncStatus to {} for {} sites", status, sites.size());
    }

    /**
     * Update the Collibra sync status for a list of projects after successful ingestion.
     */
    @Transactional
    public void updateProjectCollibraSyncStatus(List<TableauProject> projects, CollibraSyncStatus status) {
        for (TableauProject project : projects) {
            project.setCollibraSyncStatus(status);
        }
        projectRepository.saveAll(projects);
        log.debug("Updated collibraSyncStatus to {} for {} projects", status, projects.size());
    }

    /**
     * Update the Collibra sync status for a list of workbooks after successful ingestion.
     */
    @Transactional
    public void updateWorkbookCollibraSyncStatus(List<TableauWorkbook> workbooks, CollibraSyncStatus status) {
        for (TableauWorkbook workbook : workbooks) {
            workbook.setCollibraSyncStatus(status);
        }
        workbookRepository.saveAll(workbooks);
        log.debug("Updated collibraSyncStatus to {} for {} workbooks", status, workbooks.size());
    }

    /**
     * Update the Collibra sync status for a list of worksheets after successful ingestion.
     */
    @Transactional
    public void updateWorksheetCollibraSyncStatus(List<TableauWorksheet> worksheets, CollibraSyncStatus status) {
        for (TableauWorksheet worksheet : worksheets) {
            worksheet.setCollibraSyncStatus(status);
        }
        worksheetRepository.saveAll(worksheets);
        log.debug("Updated collibraSyncStatus to {} for {} worksheets", status, worksheets.size());
    }

    /**
     * Update the Collibra sync status for a list of data sources after successful ingestion.
     */
    @Transactional
    public void updateDataSourceCollibraSyncStatus(List<TableauDataSource> dataSources, CollibraSyncStatus status) {
        for (TableauDataSource dataSource : dataSources) {
            dataSource.setCollibraSyncStatus(status);
        }
        dataSourceRepository.saveAll(dataSources);
        log.debug("Updated collibraSyncStatus to {} for {} data sources", status, dataSources.size());
    }

    /**
     * Update the Collibra sync status for a list of report attributes after successful ingestion.
     */
    @Transactional
    public void updateReportAttributeCollibraSyncStatus(List<ReportAttribute> reportAttributes, CollibraSyncStatus status) {
        for (ReportAttribute reportAttribute : reportAttributes) {
            reportAttribute.setCollibraSyncStatus(status);
        }
        reportAttributeRepository.saveAll(reportAttributes);
        log.debug("Updated collibraSyncStatus to {} for {} report attributes", status, reportAttributes.size());
    }

    /**
     * Sort projects by dependency order: parent projects before child projects.
     * While the two-phase import strategy guarantees relation success regardless of ordering,
     * this sorting provides benefits for:
     * - Better hierarchy visualization in Collibra UI
     * - More logical processing order for monitoring/debugging
     * - Consistent asset creation order across runs
     * 
     * @param projects list of projects to sort
     * @return sorted list with parent projects first
     */
    private List<TableauProject> sortProjectsByDependency(List<TableauProject> projects) {
        List<TableauProject> sorted = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        
        // Build a map for quick lookup
        Map<String, TableauProject> projectMap = new HashMap<>();
        for (TableauProject project : projects) {
            String key = project.getAssetId() + ":" + project.getSiteId();
            projectMap.put(key, project);
        }
        
        // Add root projects first (those without parent)
        for (TableauProject project : projects) {
            if (project.getParentProjectId() == null || project.getParentProjectId().isEmpty()) {
                sorted.add(project);
                processed.add(project.getAssetId() + ":" + project.getSiteId());
            }
        }
        
        // Add child projects in layers
        int previousSize;
        do {
            previousSize = sorted.size();
            for (TableauProject project : projects) {
                String key = project.getAssetId() + ":" + project.getSiteId();
                if (!processed.contains(key) && project.getParentProjectId() != null) {
                    String parentKey = project.getParentProjectId() + ":" + project.getSiteId();
                    // Add if parent is already processed or doesn't exist in the current set
                    if (processed.contains(parentKey) || !projectMap.containsKey(parentKey)) {
                        sorted.add(project);
                        processed.add(key);
                    }
                }
            }
        } while (sorted.size() > previousSize); // Continue until no more projects can be added
        
        // Add any remaining projects that couldn't be ordered (orphans)
        for (TableauProject project : projects) {
            String key = project.getAssetId() + ":" + project.getSiteId();
            if (!processed.contains(key)) {
                sorted.add(project);
            }
        }
        
        return sorted;
    }

    /**
     * Sort report attributes by dependency order: non-calculated fields before calculated fields.
     * While the two-phase import strategy guarantees relation success regardless of ordering,
     * this sorting provides benefits for:
     * - Better field visualization in Collibra UI (base fields before derived)
     * - More logical processing order for monitoring/debugging
     * - Consistent asset creation order across runs
     * 
     * @param reportAttributes list of report attributes to sort
     * @return sorted list with non-calculated fields first
     */
    private List<ReportAttribute> sortReportAttributesByDependency(List<ReportAttribute> reportAttributes) {
        List<ReportAttribute> sorted = new ArrayList<>();
        
        // Add non-calculated fields first (they don't depend on others)
        for (ReportAttribute attr : reportAttributes) {
            if (!Boolean.TRUE.equals(attr.getIsCalculated())) {
                sorted.add(attr);
            }
        }
        
        // Add calculated fields last (they may depend on non-calculated fields or other calculated fields)
        for (ReportAttribute attr : reportAttributes) {
            if (Boolean.TRUE.equals(attr.getIsCalculated())) {
                sorted.add(attr);
            }
        }
        
        return sorted;
    }

    // ======================== Server Ingestion ========================

    /**
     * Ingest all servers to Collibra based on status flags.
     * First run: All assets ingested.
     * Subsequent runs: Only NEW, UPDATED, DELETED changes.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestServersToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauServer> servers = serverRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauServer> toIngest = new ArrayList<>();
        List<TableauServer> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauServer server : servers) {
            if (server.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(server);
            } else if (server.getStatusFlag() == StatusFlag.NEW || 
                       server.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapServerToCollibraAsset(server));
                toIngest.add(server);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} servers to Collibra ({} to create/update, {} to delete, {} skipped)",
                servers.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(server -> CollibraAsset.createServerIdentifierName(server.getAssetId(), server.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauServer> finalToIngest = toIngest;
        final List<TableauServer> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Server")
                .flatMap(result -> {
                    // After importing, update collibraSyncStatus for successfully ingested servers
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateServerCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    // After importing, delete the marked assets
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getServerDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                // Update collibraSyncStatus to SYNCED for deleted servers (deleted from Collibra)
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateServerCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest a single server to Collibra.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestServerToCollibra(Long serverId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return serverRepository.findById(serverId)
                .map(server -> {
                    CollibraAsset asset = mapServerToCollibraAsset(server);
                    return collibraClient.importAssets(List.of(asset), "Server")
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    updateServerCollibraSyncStatus(List.of(server), CollibraSyncStatus.SYNCED);
                                }
                            });
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Server", "Server not found: " + serverId)));
    }

    private CollibraAsset mapServerToCollibraAsset(TableauServer server) {
        String identifierName = CollibraAsset.createServerIdentifierName(server.getAssetId(), server.getName());
        
        Map<String, List<CollibraAttributeValue>> attributes = new HashMap<>();
        addAttribute(attributes, "Description", "Tableau Server: " + server.getName());
        addAttribute(attributes, "URL", server.getServerUrl());
        addAttribute(attributes, "Version", server.getVersion());

        return CollibraAsset.builder()
                .resourceType("Asset")
                .type(CollibraType.builder()
                    .name("Tableau Server")
                    .build())
                .displayName(server.getName())
                .identifier(CollibraIdentifier.builder()
                    .name(identifierName)
                    .domain(CollibraDomain.builder()
                        .name(collibraConfig.getServerDomainName())
                        .community(CollibraCommunity.builder()
                            .name(collibraConfig.getCommunityName())
                            .build())
                        .build())
                    .build())
                .attributes(attributes.isEmpty() ? null : attributes)
                .build();
    }

    // ======================== Site Ingestion ========================

    /**
     * Ingest all sites to Collibra based on status flags.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestSitesToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauSite> sites = siteRepository.findAllWithServer();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauSite> toIngest = new ArrayList<>();
        List<TableauSite> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauSite site : sites) {
            if (site.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(site);
            } else if (site.getStatusFlag() == StatusFlag.NEW || 
                       site.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapSiteToCollibraAsset(site));
                toIngest.add(site);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} sites to Collibra ({} to create/update, {} to delete, {} skipped)",
                sites.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(site -> CollibraAsset.createSiteIdentifierName(site.getAssetId(), site.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauSite> finalToIngest = toIngest;
        final List<TableauSite> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Site")
                .flatMap(result -> {
                    // After importing, update collibraSyncStatus for successfully ingested sites
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateSiteCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    // After importing, delete the marked assets
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getSiteDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                // Update collibraSyncStatus to SYNCED for deleted sites
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateSiteCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest a single site to Collibra.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestSiteToCollibra(Long siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return siteRepository.findByIdWithServer(siteId)
                .map(site -> {
                    CollibraAsset asset = mapSiteToCollibraAsset(site);
                    return collibraClient.importAssets(List.of(asset), "Site")
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    updateSiteCollibraSyncStatus(List.of(site), CollibraSyncStatus.SYNCED);
                                }
                            });
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Site", "Site not found: " + siteId)));
    }

    private CollibraAsset mapSiteToCollibraAsset(TableauSite site) {
        String identifierName = CollibraAsset.createSiteIdentifierName(site.getAssetId(), site.getName());
        
        Map<String, List<CollibraAttributeValue>> attributes = new HashMap<>();
        addAttribute(attributes, "URL", site.getSiteUrl());

        // Add relation to parent server if available
        Map<String, List<CollibraRelationTarget>> relations = new HashMap<>();
        if (site.getServer() != null) {
            TableauServer server = site.getServer();
            String serverName = CollibraAsset.createServerIdentifierName(server.getAssetId(), server.getName());
            addRelation(relations, "0195fcd7-70c3-7cda-aaec-0c5ae3dc3af7:SOURCE", serverName,
                    collibraConfig.getServerDomainName(), collibraConfig.getCommunityName());
        }

        return CollibraAsset.builder()
                .resourceType("Asset")
                .type(CollibraType.builder()
                    .name("Tableau Site")
                    .build())
                .displayName(site.getName())
                .identifier(CollibraIdentifier.builder()
                    .name(identifierName)
                    .domain(CollibraDomain.builder()
                        .name(collibraConfig.getSiteDomainName())
                        .community(CollibraCommunity.builder()
                            .name(collibraConfig.getCommunityName())
                            .build())
                        .build())
                    .build())
                .attributes(attributes.isEmpty() ? null : attributes)
                .relations(relations.isEmpty() ? null : relations)
                .build();
    }

    // ======================== Project Ingestion ========================

    /**
     * Ingest all projects to Collibra based on status flags.
     * Handles nested/child projects and their relations.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestProjectsToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauProject> projects = projectRepository.findAllWithSiteAndServer();
        
        // Create a map of projects by (assetId, siteId) to avoid N+1 queries when looking up parent projects
        Map<String, TableauProject> projectMap = new HashMap<>();
        for (TableauProject project : projects) {
            String key = project.getAssetId() + ":" + project.getSiteId();
            projectMap.put(key, project);
        }
        
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauProject> toIngest = new ArrayList<>();
        List<TableauProject> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauProject project : projects) {
            if (project.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(project);
            } else if (project.getStatusFlag() == StatusFlag.NEW || 
                       project.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapProjectToCollibraAsset(project, projectMap));
                toIngest.add(project);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} projects to Collibra ({} to create/update, {} to delete, {} skipped)",
                projects.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(project -> CollibraAsset.createProjectIdentifierName(
                    project.getSiteId(), project.getAssetId(), project.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauProject> finalToIngest = toIngest;
        final List<TableauProject> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Project")
                .flatMap(result -> {
                    // After importing, update collibraSyncStatus for successfully ingested projects
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateProjectCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    // After importing, delete the marked assets
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getProjectDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                // Update collibraSyncStatus to SYNCED for deleted projects
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateProjectCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest a single project to Collibra.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestProjectToCollibra(Long projectId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return projectRepository.findByIdWithSiteAndServer(projectId)
                .map(project -> {
                    // For single project ingestion, we still need to look up parent if needed
                    CollibraAsset asset = mapProjectToCollibraAsset(project, null);
                    return collibraClient.importAssets(List.of(asset), "Project")
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    updateProjectCollibraSyncStatus(List.of(project), CollibraSyncStatus.SYNCED);
                                }
                            });
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Project", "Project not found: " + projectId)));
    }

    private CollibraAsset mapProjectToCollibraAsset(TableauProject project, Map<String, TableauProject> projectMap) {
        // Build identifier name in format: siteid > projectid > project name
        String identifierName = CollibraAsset.createProjectIdentifierName(
            project.getSiteId(), project.getAssetId(), project.getName());
        
        // Add Description and Owner in Source attributes for Tableau Project
        Map<String, List<CollibraAttributeValue>> attributes = new HashMap<>();
        addAttribute(attributes, "Description", project.getDescription());
        addAttribute(attributes, "Owner in Source", project.getOwner());

        // Add relations to parent project and site
        Map<String, List<CollibraRelationTarget>> relations = new HashMap<>();
        
        // Add relation to parent project if this is a nested project
        if (project.getParentProjectId() != null && !project.getParentProjectId().isEmpty()) {
            TableauProject parentProject = null;
            
            // Use the project map if provided (batch ingestion), otherwise query the database (single ingestion)
            if (projectMap != null) {
                String parentKey = project.getParentProjectId() + ":" + project.getSiteId();
                parentProject = projectMap.get(parentKey);
            } else {
                parentProject = projectRepository.findByAssetIdAndSiteId(project.getParentProjectId(), project.getSiteId())
                        .orElse(null);
            }
            
            if (parentProject != null) {
                String parentName = CollibraAsset.createProjectIdentifierName(
                    parentProject.getSiteId(), parentProject.getAssetId(), parentProject.getName());
                addRelation(relations, "00000000-0000-0000-0000-120000000001:SOURCE", parentName,
                        collibraConfig.getProjectDomainName(), collibraConfig.getCommunityName());
            }
        }

        // Add relation to parent site
        if (project.getSite() != null) {
            TableauSite site = project.getSite();
            String siteName = CollibraAsset.createSiteIdentifierName(site.getAssetId(), site.getName());
            addRelation(relations, "0195fc55-b49f-7711-9ce6-d87a1f60b36a:SOURCE", siteName,
                    collibraConfig.getSiteDomainName(), collibraConfig.getCommunityName());
        }

        return CollibraAsset.builder()
                .resourceType("Asset")
                .type(CollibraType.builder()
                    .name("Tableau Project")
                    .build())
                .displayName(project.getName())
                .identifier(CollibraIdentifier.builder()
                    .name(identifierName)
                    .domain(CollibraDomain.builder()
                        .name(collibraConfig.getProjectDomainName())
                        .community(CollibraCommunity.builder()
                            .name(collibraConfig.getCommunityName())
                            .build())
                        .build())
                    .build())
                .attributes(attributes.isEmpty() ? null : attributes)
                .relations(relations.isEmpty() ? null : relations)
                .build();
    }

    // ======================== Workbook Ingestion ========================

    /**
     * Ingest all workbooks to Collibra based on status flags.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestWorkbooksToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorkbook> workbooks = workbookRepository.findAllWithProject();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauWorkbook> toIngest = new ArrayList<>();
        List<TableauWorkbook> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauWorkbook workbook : workbooks) {
            if (workbook.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(workbook);
            } else if (workbook.getStatusFlag() == StatusFlag.NEW || 
                       workbook.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapWorkbookToCollibraAsset(workbook));
                toIngest.add(workbook);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} workbooks to Collibra ({} to create/update, {} to delete, {} skipped)",
                workbooks.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(workbook -> CollibraAsset.createWorkbookIdentifierName(
                    workbook.getSiteId(), workbook.getAssetId(), workbook.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauWorkbook> finalToIngest = toIngest;
        final List<TableauWorkbook> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Workbook")
                .flatMap(result -> {
                    // After importing, update collibraSyncStatus for successfully ingested workbooks
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateWorkbookCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    // After importing, delete the marked assets
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getWorkbookDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                // Update collibraSyncStatus to SYNCED for deleted workbooks
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateWorkbookCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest a single workbook to Collibra.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestWorkbookToCollibra(Long workbookId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return workbookRepository.findById(workbookId)
                .map(workbook -> {
                    CollibraAsset asset = mapWorkbookToCollibraAsset(workbook);
                    return collibraClient.importAssets(List.of(asset), "Workbook")
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    updateWorkbookCollibraSyncStatus(List.of(workbook), CollibraSyncStatus.SYNCED);
                                }
                            });
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Workbook", "Workbook not found: " + workbookId)));
    }

    private CollibraAsset mapWorkbookToCollibraAsset(TableauWorkbook workbook) {
        String identifierName = CollibraAsset.createWorkbookIdentifierName(
            workbook.getSiteId(), workbook.getAssetId(), workbook.getName());
        
        Map<String, List<CollibraAttributeValue>> attributes = new HashMap<>();
        addAttribute(attributes, "Description", workbook.getDescription());
        addAttribute(attributes, "Owner in Source", workbook.getOwner());
        
        // Add Document creation date (from tableauCreatedAt)
        if (workbook.getTableauCreatedAt() != null) {
            addAttribute(attributes, "Document creation date", 
                convertToUnixTimestamp(workbook.getTableauCreatedAt()));
        }
        
        // Add Document modification date (from tableauUpdatedAt)
        if (workbook.getTableauUpdatedAt() != null) {
            addAttribute(attributes, "Document modification date", 
                convertToUnixTimestamp(workbook.getTableauUpdatedAt()));
        }

        // Add relation to parent project
        Map<String, List<CollibraRelationTarget>> relations = new HashMap<>();
        if (workbook.getProject() != null) {
            TableauProject project = workbook.getProject();
            String projectName = CollibraAsset.createProjectIdentifierName(
                project.getSiteId(), project.getAssetId(), project.getName());
            addRelation(relations, "0195fcea-cc73-7284-88a6-ea770982b1ba:SOURCE", projectName,
                    collibraConfig.getProjectDomainName(), collibraConfig.getCommunityName());
        }

        return CollibraAsset.builder()
                .resourceType("Asset")
                .type(CollibraType.builder()
                    .name("Tableau Workbook")
                    .build())
                .displayName(workbook.getName())
                .identifier(CollibraIdentifier.builder()
                    .name(identifierName)
                    .domain(CollibraDomain.builder()
                        .name(collibraConfig.getWorkbookDomainName())
                        .community(CollibraCommunity.builder()
                            .name(collibraConfig.getCommunityName())
                            .build())
                        .build())
                    .build())
                .attributes(attributes.isEmpty() ? null : attributes)
                .relations(relations.isEmpty() ? null : relations)
                .build();
    }

    // ======================== Worksheet Ingestion ========================

    /**
     * Ingest all worksheets to Collibra based on status flags.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestWorksheetsToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorksheet> worksheets = worksheetRepository.findAllWithWorkbook();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauWorksheet> toIngest = new ArrayList<>();
        List<TableauWorksheet> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauWorksheet worksheet : worksheets) {
            if (worksheet.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(worksheet);
            } else if (worksheet.getStatusFlag() == StatusFlag.NEW || 
                       worksheet.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapWorksheetToCollibraAsset(worksheet));
                toIngest.add(worksheet);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} worksheets to Collibra ({} to create/update, {} to delete, {} skipped)",
                worksheets.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(worksheet -> CollibraAsset.createWorksheetIdentifierName(
                    worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauWorksheet> finalToIngest = toIngest;
        final List<TableauWorksheet> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Worksheet")
                .flatMap(result -> {
                    // After importing, update collibraSyncStatus for successfully ingested worksheets
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateWorksheetCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    // After importing, delete the marked assets
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getWorksheetDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                // Update collibraSyncStatus to SYNCED for deleted worksheets
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateWorksheetCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest a single worksheet to Collibra.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestWorksheetToCollibra(Long worksheetId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return worksheetRepository.findById(worksheetId)
                .map(worksheet -> {
                    CollibraAsset asset = mapWorksheetToCollibraAsset(worksheet);
                    return collibraClient.importAssets(List.of(asset), "Worksheet")
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    updateWorksheetCollibraSyncStatus(List.of(worksheet), CollibraSyncStatus.SYNCED);
                                }
                            });
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("Worksheet", "Worksheet not found: " + worksheetId)));
    }

    private CollibraAsset mapWorksheetToCollibraAsset(TableauWorksheet worksheet) {
        String identifierName = CollibraAsset.createWorksheetIdentifierName(
            worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName());
        
        // No attributes for Tableau Worksheet as per requirements

        // Add relation to parent workbook
        Map<String, List<CollibraRelationTarget>> relations = new HashMap<>();
        if (worksheet.getWorkbook() != null) {
            TableauWorkbook workbook = worksheet.getWorkbook();
            String workbookName = CollibraAsset.createWorkbookIdentifierName(
                workbook.getSiteId(), workbook.getAssetId(), workbook.getName());
            addRelation(relations, "0195fd0b-f14f-7e72-a382-750d4f3a704e:SOURCE", workbookName,
                    collibraConfig.getWorkbookDomainName(), collibraConfig.getCommunityName());
        }

        return CollibraAsset.builder()
                .resourceType("Asset")
                .type(CollibraType.builder()
                    .name("Tableau Worksheet")
                    .build())
                .displayName(worksheet.getName())
                .identifier(CollibraIdentifier.builder()
                    .name(identifierName)
                    .domain(CollibraDomain.builder()
                        .name(collibraConfig.getWorksheetDomainName())
                        .community(CollibraCommunity.builder()
                            .name(collibraConfig.getCommunityName())
                            .build())
                        .build())
                    .build())
                .attributes(null)
                .relations(relations.isEmpty() ? null : relations)
                .build();
    }

    // ======================== DataSource Ingestion ========================

    /**
     * Ingest all data sources to Collibra based on status flags.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestDataSourcesToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauDataSource> dataSources = dataSourceRepository.findAll();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauDataSource> toIngest = new ArrayList<>();
        List<TableauDataSource> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauDataSource dataSource : dataSources) {
            if (dataSource.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(dataSource);
            } else if (dataSource.getStatusFlag() == StatusFlag.NEW || 
                       dataSource.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapDataSourceToCollibraAsset(dataSource));
                toIngest.add(dataSource);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} data sources to Collibra ({} to create/update, {} to delete, {} skipped)",
                dataSources.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(dataSource -> CollibraAsset.createIdentifierName(
                    dataSource.getAssetId(), dataSource.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauDataSource> finalToIngest = toIngest;
        final List<TableauDataSource> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "DataSource")
                .flatMap(result -> {
                    // After importing, update collibraSyncStatus for successfully ingested data sources
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateDataSourceCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    // After importing, delete the marked assets
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getDatasourceDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                // Update collibraSyncStatus to SYNCED for deleted data sources
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateDataSourceCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest a single data source to Collibra.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestDataSourceToCollibra(Long dataSourceId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        return dataSourceRepository.findById(dataSourceId)
                .map(dataSource -> {
                    CollibraAsset asset = mapDataSourceToCollibraAsset(dataSource);
                    return collibraClient.importAssets(List.of(asset), "DataSource")
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    updateDataSourceCollibraSyncStatus(List.of(dataSource), CollibraSyncStatus.SYNCED);
                                }
                            });
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("DataSource", "DataSource not found: " + dataSourceId)));
    }

    private CollibraAsset mapDataSourceToCollibraAsset(TableauDataSource dataSource) {
        String identifierName = CollibraAsset.createIdentifierName(dataSource.getAssetId(), dataSource.getName());
        
        Map<String, List<CollibraAttributeValue>> attributes = new HashMap<>();
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
        Map<String, List<CollibraRelationTarget>> relations = new HashMap<>();
        if (dataSource.getWorkbook() != null) {
            TableauWorkbook workbook = dataSource.getWorkbook();
            String workbookName = CollibraAsset.createWorkbookIdentifierName(
                workbook.getSiteId(), workbook.getAssetId(), workbook.getName());
            addRelation(relations, "relationid:SOURCE", workbookName,
                    collibraConfig.getWorkbookDomainName(), collibraConfig.getCommunityName());
        }

        return CollibraAsset.builder()
                .resourceType("Asset")
                .type(CollibraType.builder()
                    .name("Tableau Data Source")
                    .build())
                .displayName(dataSource.getName())
                .identifier(CollibraIdentifier.builder()
                    .name(identifierName)
                    .domain(CollibraDomain.builder()
                        .name(collibraConfig.getDatasourceDomainName())
                        .community(CollibraCommunity.builder()
                            .name(collibraConfig.getCommunityName())
                            .build())
                        .build())
                    .build())
                .attributes(attributes.isEmpty() ? null : attributes)
                .relations(relations.isEmpty() ? null : relations)
                .build();
    }

    // ======================== Report Attribute Ingestion ========================

    /**
     * Ingest all report attributes to Collibra based on status flags.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestReportAttributesToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        // Use findAllWithRelations() to eagerly fetch worksheet and dataSource relationships
        // This prevents LazyInitializationException when mapping to Collibra assets
        List<ReportAttribute> reportAttributes = reportAttributeRepository.findAllWithRelations();
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<ReportAttribute> toIngest = new ArrayList<>();
        List<ReportAttribute> toDelete = new ArrayList<>();
        int skipped = 0;

        for (ReportAttribute attr : reportAttributes) {
            if (attr.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(attr);
            } else if (attr.getStatusFlag() == StatusFlag.NEW || 
                       attr.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapReportAttributeToCollibraAsset(attr));
                toIngest.add(attr);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} report attributes to Collibra ({} to create/update, {} to delete, {} skipped)",
                reportAttributes.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(attr -> CollibraAsset.createReportAttributeIdentifierName(
                    attr.getSiteId(), attr.getWorksheetId(), attr.getAssetId(), attr.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<ReportAttribute> finalToIngest = toIngest;
        final List<ReportAttribute> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "ReportAttribute")
                .flatMap(result -> {
                    // After importing, update collibraSyncStatus for successfully ingested report attributes
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateReportAttributeCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    // After importing, delete the marked assets
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getReportAttributeDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                // Update collibraSyncStatus to SYNCED for deleted report attributes
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateReportAttributeCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest a single report attribute to Collibra.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     */
    public Mono<CollibraIngestionResult> ingestReportAttributeToCollibra(Long reportAttributeId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        // Use findByIdWithRelations to eagerly fetch worksheet and dataSource relationships
        // This prevents LazyInitializationException when mapping to Collibra assets
        return reportAttributeRepository.findByIdWithRelations(reportAttributeId)
                .map(attr -> {
                    CollibraAsset asset = mapReportAttributeToCollibraAsset(attr);
                    return collibraClient.importAssets(List.of(asset), "ReportAttribute")
                            .doOnSuccess(result -> {
                                if (result.isSuccess()) {
                                    updateReportAttributeCollibraSyncStatus(List.of(attr), CollibraSyncStatus.SYNCED);
                                }
                            });
                })
                .orElse(Mono.just(CollibraIngestionResult.failure("ReportAttribute", "ReportAttribute not found: " + reportAttributeId)));
    }

    private CollibraAsset mapReportAttributeToCollibraAsset(ReportAttribute attr) {
        String identifierName = CollibraAsset.createReportAttributeIdentifierName(
            attr.getSiteId(), attr.getWorksheetId(), attr.getAssetId(), attr.getName());
        
        Map<String, List<CollibraAttributeValue>> attributes = new HashMap<>();
        // Only include the three required attributes as specified in the requirement
        addAttribute(attributes, "Technical Data Type", attr.getDataType());
        addAttribute(attributes, "Role in Report", capitalizeFieldRole(attr.getFieldRole()));
        addAttribute(attributes, "Calculation Rule", attr.getCalculationLogic());

        // Add relation to parent worksheet
        Map<String, List<CollibraRelationTarget>> relations = new HashMap<>();
        
        // Add relation to parent worksheet using the specified UUID
        // 0195fd1e-47f7-7674-96eb-e91ff0ce71c4:SOURCE - Tableau Worksheet contains Tableau Report Attribute
        if (attr.getWorksheet() != null) {
            TableauWorksheet worksheet = attr.getWorksheet();
            String worksheetName = CollibraAsset.createWorksheetIdentifierName(
                worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName());
            addRelation(relations, "0195fd1e-47f7-7674-96eb-e91ff0ce71c4:SOURCE", worksheetName,
                    collibraConfig.getWorksheetDomainName(), collibraConfig.getCommunityName());
        }

        return CollibraAsset.builder()
                .resourceType("Asset")
                .type(CollibraType.builder()
                    .name("Tableau Report Attribute")
                    .build())
                .displayName(attr.getName())
                .identifier(CollibraIdentifier.builder()
                    .name(identifierName)
                    .domain(CollibraDomain.builder()
                        .name(collibraConfig.getReportAttributeDomainName())
                        .community(CollibraCommunity.builder()
                            .name(collibraConfig.getCommunityName())
                            .build())
                        .build())
                    .build())
                .attributes(attributes.isEmpty() ? null : attributes)
                .relations(relations.isEmpty() ? null : relations)
                .build();
    }

    // ======================== Bulk Ingestion ========================

    /**
     * Ingest servers without performing deletions (for use in bulk ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestServersWithoutDeletion(List<DeletionInfo> deletionList) {
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

        log.info("Ingesting {} servers to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                servers.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(server -> CollibraAsset.createServerIdentifierName(server.getAssetId(), server.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getServerDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Server")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest sites without performing deletions (for use in bulk ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestSitesWithoutDeletion(List<DeletionInfo> deletionList) {
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

        log.info("Ingesting {} sites to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                sites.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(site -> CollibraAsset.createSiteIdentifierName(site.getAssetId(), site.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getSiteDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Site")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest projects without performing deletions (for use in bulk ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestProjectsWithoutDeletion(List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauProject> projects = projectRepository.findAllWithSiteAndServer();
        
        // Sort projects by dependency: parents before children
        projects = sortProjectsByDependency(projects);
        
        // Build a map of projects for efficient parent lookup
        Map<String, TableauProject> projectMap = new HashMap<>();
        for (TableauProject project : projects) {
            String key = project.getAssetId() + ":" + project.getSiteId();
            projectMap.put(key, project);
        }
        
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauProject> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauProject project : projects) {
            if (project.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(project);
            } else if (project.getStatusFlag() == StatusFlag.NEW || 
                       project.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapProjectToCollibraAsset(project, projectMap));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} projects to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                projects.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(project -> CollibraAsset.createProjectIdentifierName(
                        project.getSiteId(), project.getAssetId(), project.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getProjectDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Project")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest workbooks without performing deletions (for use in bulk ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestWorkbooksWithoutDeletion(List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorkbook> workbooks = workbookRepository.findAllWithProject();
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

        log.info("Ingesting {} workbooks to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                workbooks.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(workbook -> CollibraAsset.createWorkbookIdentifierName(
                        workbook.getSiteId(), workbook.getAssetId(), workbook.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getWorkbookDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Workbook")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest worksheets without performing deletions (for use in bulk ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestWorksheetsWithoutDeletion(List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorksheet> worksheets = worksheetRepository.findAllWithWorkbook();
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

        log.info("Ingesting {} worksheets to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                worksheets.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(worksheet -> CollibraAsset.createWorksheetIdentifierName(
                        worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getWorksheetDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Worksheet")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest data sources without performing deletions (for use in bulk ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestDataSourcesWithoutDeletion(List<DeletionInfo> deletionList) {
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

        log.info("Ingesting {} data sources to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                dataSources.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(dataSource -> CollibraAsset.createIdentifierName(
                        dataSource.getAssetId(), dataSource.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getDatasourceDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "DataSource")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest report attributes without performing deletions (for use in bulk ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestReportAttributesWithoutDeletion(List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        // Use findAllWithRelations to eagerly fetch worksheet and dataSource relationships
        // This prevents LazyInitializationException when mapping to Collibra assets
        List<ReportAttribute> reportAttributes = reportAttributeRepository.findAllWithRelations();
        
        // Sort report attributes by dependency: non-calculated fields before calculated fields
        reportAttributes = sortReportAttributesByDependency(reportAttributes);
        
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

        log.info("Ingesting {} report attributes to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                reportAttributes.size(), assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(attr -> CollibraAsset.createReportAttributeIdentifierName(
                        attr.getSiteId(), attr.getWorksheetId(), attr.getAssetId(), attr.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getReportAttributeDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "ReportAttribute")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest all asset types to Collibra in the correct order.
     */
    public Mono<CollibraIngestionResult> ingestAllToCollibra() {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        log.info("Starting full ingestion of all assets to Collibra");
        
        // Collect all deletion identifiers across all asset types
        List<DeletionInfo> allDeletions = new ArrayList<>();

        return ingestServersWithoutDeletion(allDeletions)
                .flatMap(serverResult -> {
                    log.info("Server ingestion complete: {}", serverResult);
                    return ingestSitesWithoutDeletion(allDeletions);
                })
                .flatMap(siteResult -> {
                    log.info("Site ingestion complete: {}", siteResult);
                    return ingestProjectsWithoutDeletion(allDeletions);
                })
                .flatMap(projectResult -> {
                    log.info("Project ingestion complete: {}", projectResult);
                    return ingestWorkbooksWithoutDeletion(allDeletions);
                })
                .flatMap(workbookResult -> {
                    log.info("Workbook ingestion complete: {}", workbookResult);
                    return ingestWorksheetsWithoutDeletion(allDeletions);
                })
                .flatMap(worksheetResult -> {
                    log.info("Worksheet ingestion complete: {}", worksheetResult);
                    return ingestDataSourcesWithoutDeletion(allDeletions);
                })
                .flatMap(dataSourceResult -> {
                    log.info("DataSource ingestion complete: {}", dataSourceResult);
                    return ingestReportAttributesWithoutDeletion(allDeletions);
                })
                .flatMap(reportAttrResult -> {
                    log.info("ReportAttribute ingestion complete: {}", reportAttrResult);
                    // Now perform all deletions after all assets have been imported
                    log.info("Performing all deletions ({} total) after all imports", allDeletions.size());
                    return performAllDeletions(allDeletions);
                })
                .map(deletionCount -> {
                    log.info("Full ingestion to Collibra completed successfully with {} deletions", deletionCount);
                    return CollibraIngestionResult.builder()
                            .assetType("All")
                            .success(true)
                            .assetsDeleted(deletionCount)
                            .message("Full ingestion to Collibra completed successfully")
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Full ingestion failed: {}", e.getMessage(), e);
                    // Attempt to perform deletions even if import failed
                    log.warn("Attempting to perform deletions despite import failure");
                    return performAllDeletions(allDeletions)
                            .flatMap(deletionCount -> {
                                log.info("Deletions completed: {} assets deleted", deletionCount);
                                return Mono.just(CollibraIngestionResult.failure("All", 
                                        "Full ingestion failed: " + e.getMessage() + 
                                        " (Note: " + deletionCount + " deletions were completed)"));
                            })
                            .onErrorResume(deleteError -> {
                                log.error("Deletions also failed: {}", deleteError.getMessage(), deleteError);
                                return Mono.just(CollibraIngestionResult.failure("All", 
                                        "Full ingestion failed: " + e.getMessage() + 
                                        " (Note: Deletions also failed: " + deleteError.getMessage() + ")"));
                            });
                });
    }

    // ======================== Site-Level Ingestion ========================

    /**
     * Ingest all projects for a specific site to Collibra based on status flags.
     * This reduces load on Collibra by processing assets site by site.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     * 
     * @param siteId the Tableau site ID (assetId of the site)
     * @return ingestion result for projects in the specified site
     */
    public Mono<CollibraIngestionResult> ingestProjectsBySiteToCollibra(String siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauProject> projects = projectRepository.findAllBySiteIdWithSiteAndServer(siteId);
        
        // Create a map of projects by (assetId, siteId) to avoid N+1 queries when looking up parent projects
        Map<String, TableauProject> projectMap = new HashMap<>();
        for (TableauProject project : projects) {
            String key = project.getAssetId() + ":" + project.getSiteId();
            projectMap.put(key, project);
        }
        
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauProject> toIngest = new ArrayList<>();
        List<TableauProject> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauProject project : projects) {
            if (project.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(project);
            } else if (project.getStatusFlag() == StatusFlag.NEW || 
                       project.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapProjectToCollibraAsset(project, projectMap));
                toIngest.add(project);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} projects for site {} to Collibra ({} to create/update, {} to delete, {} skipped)",
                projects.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(project -> CollibraAsset.createProjectIdentifierName(
                    project.getSiteId(), project.getAssetId(), project.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauProject> finalToIngest = toIngest;
        final List<TableauProject> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Project")
                .flatMap(result -> {
                    // Update collibraSyncStatus for successfully ingested projects
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateProjectCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getProjectDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateProjectCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest all workbooks for a specific site to Collibra based on status flags.
     * This reduces load on Collibra by processing assets site by site.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     * 
     * @param siteId the Tableau site ID (assetId of the site)
     * @return ingestion result for workbooks in the specified site
     */
    public Mono<CollibraIngestionResult> ingestWorkbooksBySiteToCollibra(String siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorkbook> workbooks = workbookRepository.findAllBySiteIdWithProject(siteId);
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauWorkbook> toIngest = new ArrayList<>();
        List<TableauWorkbook> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauWorkbook workbook : workbooks) {
            if (workbook.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(workbook);
            } else if (workbook.getStatusFlag() == StatusFlag.NEW || 
                       workbook.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapWorkbookToCollibraAsset(workbook));
                toIngest.add(workbook);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} workbooks for site {} to Collibra ({} to create/update, {} to delete, {} skipped)",
                workbooks.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(workbook -> CollibraAsset.createWorkbookIdentifierName(
                    workbook.getSiteId(), workbook.getAssetId(), workbook.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauWorkbook> finalToIngest = toIngest;
        final List<TableauWorkbook> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Workbook")
                .flatMap(result -> {
                    // Update collibraSyncStatus for successfully ingested workbooks
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateWorkbookCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getWorkbookDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateWorkbookCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest all worksheets for a specific site to Collibra based on status flags.
     * This reduces load on Collibra by processing assets site by site.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     * 
     * @param siteId the Tableau site ID (assetId of the site)
     * @return ingestion result for worksheets in the specified site
     */
    public Mono<CollibraIngestionResult> ingestWorksheetsBySiteToCollibra(String siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorksheet> worksheets = worksheetRepository.findAllBySiteIdWithWorkbook(siteId);
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauWorksheet> toIngest = new ArrayList<>();
        List<TableauWorksheet> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauWorksheet worksheet : worksheets) {
            if (worksheet.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(worksheet);
            } else if (worksheet.getStatusFlag() == StatusFlag.NEW || 
                       worksheet.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapWorksheetToCollibraAsset(worksheet));
                toIngest.add(worksheet);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} worksheets for site {} to Collibra ({} to create/update, {} to delete, {} skipped)",
                worksheets.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(worksheet -> CollibraAsset.createWorksheetIdentifierName(
                    worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauWorksheet> finalToIngest = toIngest;
        final List<TableauWorksheet> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "Worksheet")
                .flatMap(result -> {
                    // Update collibraSyncStatus for successfully ingested worksheets
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateWorksheetCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getWorksheetDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateWorksheetCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest all data sources for a specific site to Collibra based on status flags.
     * This reduces load on Collibra by processing assets site by site.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     * 
     * @param siteId the Tableau site ID (assetId of the site)
     * @return ingestion result for data sources in the specified site
     */
    public Mono<CollibraIngestionResult> ingestDataSourcesBySiteToCollibra(String siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauDataSource> dataSources = dataSourceRepository.findBySiteId(siteId);
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauDataSource> toIngest = new ArrayList<>();
        List<TableauDataSource> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauDataSource dataSource : dataSources) {
            if (dataSource.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(dataSource);
            } else if (dataSource.getStatusFlag() == StatusFlag.NEW || 
                       dataSource.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapDataSourceToCollibraAsset(dataSource));
                toIngest.add(dataSource);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} data sources for site {} to Collibra ({} to create/update, {} to delete, {} skipped)",
                dataSources.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(dataSource -> CollibraAsset.createIdentifierName(
                    dataSource.getAssetId(), dataSource.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<TableauDataSource> finalToIngest = toIngest;
        final List<TableauDataSource> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "DataSource")
                .flatMap(result -> {
                    // Update collibraSyncStatus for successfully ingested data sources
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateDataSourceCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getDatasourceDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateDataSourceCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest all report attributes for a specific site to Collibra based on status flags.
     * This reduces load on Collibra by processing assets site by site.
     * Updates collibraSyncStatus to SYNCED after successful ingestion.
     * 
     * @param siteId the Tableau site ID (assetId of the site)
     * @return ingestion result for report attributes in the specified site
     */
    public Mono<CollibraIngestionResult> ingestReportAttributesBySiteToCollibra(String siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<ReportAttribute> reportAttributes = reportAttributeRepository.findBySiteIdWithRelations(siteId);
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<ReportAttribute> toIngest = new ArrayList<>();
        List<ReportAttribute> toDelete = new ArrayList<>();
        int skipped = 0;

        for (ReportAttribute attr : reportAttributes) {
            if (attr.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(attr);
            } else if (attr.getStatusFlag() == StatusFlag.NEW || 
                       attr.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapReportAttributeToCollibraAsset(attr));
                toIngest.add(attr);
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} report attributes for site {} to Collibra ({} to create/update, {} to delete, {} skipped)",
                reportAttributes.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Build list of identifiers for assets to delete
        List<String> identifiersToDelete = toDelete.stream()
                .map(attr -> CollibraAsset.createReportAttributeIdentifierName(
                    attr.getSiteId(), attr.getWorksheetId(), attr.getAssetId(), attr.getName()))
                .toList();

        final int finalSkipped = skipped;
        final List<ReportAttribute> finalToIngest = toIngest;
        final List<ReportAttribute> finalToDelete = toDelete;
        
        return collibraClient.importAssets(assetsToIngest, "ReportAttribute")
                .flatMap(result -> {
                    // Update collibraSyncStatus for successfully ingested report attributes
                    if (result.isSuccess() && !finalToIngest.isEmpty()) {
                        updateReportAttributeCollibraSyncStatus(finalToIngest, CollibraSyncStatus.SYNCED);
                    }
                    
                    return deleteAssetsFromCollibra(identifiersToDelete, 
                            collibraConfig.getReportAttributeDomainName(), 
                            collibraConfig.getCommunityName())
                            .map(deletedCount -> {
                                if (deletedCount > 0 && !finalToDelete.isEmpty()) {
                                    updateReportAttributeCollibraSyncStatus(finalToDelete, CollibraSyncStatus.SYNCED);
                                }
                                result.setAssetsDeleted(deletedCount);
                                result.setAssetsSkipped(finalSkipped);
                                return result;
                            });
                });
    }

    /**
     * Ingest projects for a specific site without performing deletions (for use in bulk site ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestProjectsBySiteWithoutDeletion(String siteId, List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauProject> projects = projectRepository.findAllBySiteIdWithSiteAndServer(siteId);
        
        // Sort projects by dependency: parents before children
        projects = sortProjectsByDependency(projects);
        
        // Create a map of projects by (assetId, siteId) to avoid N+1 queries when looking up parent projects
        Map<String, TableauProject> projectMap = new HashMap<>();
        for (TableauProject project : projects) {
            String key = project.getAssetId() + ":" + project.getSiteId();
            projectMap.put(key, project);
        }
        
        List<CollibraAsset> assetsToIngest = new ArrayList<>();
        List<TableauProject> toDelete = new ArrayList<>();
        int skipped = 0;

        for (TableauProject project : projects) {
            if (project.getStatusFlag() == StatusFlag.DELETED) {
                toDelete.add(project);
            } else if (project.getStatusFlag() == StatusFlag.NEW || 
                       project.getStatusFlag() == StatusFlag.UPDATED) {
                assetsToIngest.add(mapProjectToCollibraAsset(project, projectMap));
            } else {
                skipped++;
            }
        }

        log.info("Ingesting {} projects for site {} to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                projects.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(project -> CollibraAsset.createProjectIdentifierName(
                        project.getSiteId(), project.getAssetId(), project.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getProjectDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Project")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest workbooks for a specific site without performing deletions (for use in bulk site ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestWorkbooksBySiteWithoutDeletion(String siteId, List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorkbook> workbooks = workbookRepository.findAllBySiteIdWithProject(siteId);
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

        log.info("Ingesting {} workbooks for site {} to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                workbooks.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(workbook -> CollibraAsset.createWorkbookIdentifierName(
                        workbook.getSiteId(), workbook.getAssetId(), workbook.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getWorkbookDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Workbook")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest worksheets for a specific site without performing deletions (for use in bulk site ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestWorksheetsBySiteWithoutDeletion(String siteId, List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauWorksheet> worksheets = worksheetRepository.findAllBySiteIdWithWorkbook(siteId);
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

        log.info("Ingesting {} worksheets for site {} to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                worksheets.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(worksheet -> CollibraAsset.createWorksheetIdentifierName(
                        worksheet.getSiteId(), worksheet.getAssetId(), worksheet.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getWorksheetDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "Worksheet")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest data sources for a specific site without performing deletions (for use in bulk site ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestDataSourcesBySiteWithoutDeletion(String siteId, List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<TableauDataSource> dataSources = dataSourceRepository.findBySiteId(siteId);
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

        log.info("Ingesting {} data sources for site {} to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                dataSources.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(dataSource -> CollibraAsset.createIdentifierName(
                        dataSource.getAssetId(), dataSource.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getDatasourceDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "DataSource")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest report attributes for a specific site without performing deletions (for use in bulk site ingestion).
     * Collects deletion identifiers in the provided list for deferred deletion.
     */
    private Mono<CollibraIngestionResult> ingestReportAttributesBySiteWithoutDeletion(String siteId, List<DeletionInfo> deletionList) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        List<ReportAttribute> reportAttributes = reportAttributeRepository.findBySiteIdWithRelations(siteId);
        
        // Sort report attributes by dependency: non-calculated fields before calculated fields
        reportAttributes = sortReportAttributesByDependency(reportAttributes);
        
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

        log.info("Ingesting {} report attributes for site {} to Collibra ({} to create/update, {} to delete deferred, {} skipped)",
                reportAttributes.size(), siteId, assetsToIngest.size(), toDelete.size(), skipped);

        // Collect identifiers for deferred deletion
        if (!toDelete.isEmpty()) {
            List<String> identifiersToDelete = toDelete.stream()
                    .map(attr -> CollibraAsset.createReportAttributeIdentifierName(
                        attr.getSiteId(), attr.getWorksheetId(), attr.getAssetId(), attr.getName()))
                    .toList();
            deletionList.add(new DeletionInfo(identifiersToDelete, 
                    collibraConfig.getReportAttributeDomainName(), 
                    collibraConfig.getCommunityName()));
        }

        final int finalSkipped = skipped;
        return collibraClient.importAssets(assetsToIngest, "ReportAttribute")
                .map(result -> {
                    result.setAssetsSkipped(finalSkipped);
                    return result;
                });
    }

    /**
     * Ingest all assets for a specific site to Collibra in the correct order.
     * This reduces load on Collibra by processing assets site by site instead of all at once.
     * 
     * @param siteId the Tableau site ID (assetId of the site)
     * @return ingestion result for all assets in the specified site
     */
    public Mono<CollibraIngestionResult> ingestAllBySiteToCollibra(String siteId) {
        if (!isConfigured()) {
            return Mono.just(CollibraIngestionResult.notConfigured());
        }

        log.info("Starting site-level ingestion of all assets for site {} to Collibra", siteId);

        // Collect all deletion identifiers across all asset types for this site
        List<DeletionInfo> allDeletions = new ArrayList<>();

        return ingestProjectsBySiteWithoutDeletion(siteId, allDeletions)
                .flatMap(projectResult -> {
                    log.info("Site {} - Project ingestion complete: {}", siteId, projectResult);
                    return ingestWorkbooksBySiteWithoutDeletion(siteId, allDeletions);
                })
                .flatMap(workbookResult -> {
                    log.info("Site {} - Workbook ingestion complete: {}", siteId, workbookResult);
                    return ingestWorksheetsBySiteWithoutDeletion(siteId, allDeletions);
                })
                .flatMap(worksheetResult -> {
                    log.info("Site {} - Worksheet ingestion complete: {}", siteId, worksheetResult);
                    return ingestDataSourcesBySiteWithoutDeletion(siteId, allDeletions);
                })
                .flatMap(dataSourceResult -> {
                    log.info("Site {} - DataSource ingestion complete: {}", siteId, dataSourceResult);
                    return ingestReportAttributesBySiteWithoutDeletion(siteId, allDeletions);
                })
                .flatMap(reportAttrResult -> {
                    log.info("Site {} - ReportAttribute ingestion complete: {}", siteId, reportAttrResult);
                    // Now perform all deletions after all assets have been imported
                    log.info("Site {} - Performing all deletions ({} total) after all imports", siteId, allDeletions.size());
                    return performAllDeletions(allDeletions);
                })
                .map(deletionCount -> {
                    log.info("Site {} - Full ingestion to Collibra completed successfully with {} deletions", 
                            siteId, deletionCount);
                    return CollibraIngestionResult.builder()
                            .assetType("All (Site: " + siteId + ")")
                            .success(true)
                            .assetsDeleted(deletionCount)
                            .message("Site-level ingestion to Collibra completed successfully for site: " + siteId)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Site {} - Full ingestion failed: {}", siteId, e.getMessage(), e);
                    // Attempt to perform deletions even if import failed
                    log.warn("Site {} - Attempting to perform deletions despite import failure", siteId);
                    return performAllDeletions(allDeletions)
                            .flatMap(deletionCount -> {
                                log.info("Site {} - Deletions completed: {} assets deleted", siteId, deletionCount);
                                return Mono.just(CollibraIngestionResult.failure("All (Site: " + siteId + ")", 
                                        "Site-level ingestion failed for site " + siteId + ": " + e.getMessage() +
                                        " (Note: " + deletionCount + " deletions were completed)"));
                            })
                            .onErrorResume(deleteError -> {
                                log.error("Site {} - Deletions also failed: {}", siteId, deleteError.getMessage(), deleteError);
                                return Mono.just(CollibraIngestionResult.failure("All (Site: " + siteId + ")", 
                                        "Site-level ingestion failed for site " + siteId + ": " + e.getMessage() +
                                        " (Note: Deletions also failed: " + deleteError.getMessage() + ")"));
                            });
                });
    }

    // ======================== Helper Methods ========================

    /**
     * Delete assets from Collibra by their identifier names, domain, and community.
     * Returns a Mono with the count of successfully deleted assets.
     */
    private Mono<Integer> deleteAssetsFromCollibra(List<String> identifierNames, String domainName, String communityName) {
        if (identifierNames.isEmpty()) {
            return Mono.just(0);
        }

        log.debug("Attempting to delete {} assets from Collibra (domain: {}, community: {})", 
                identifierNames.size(), domainName, communityName);

        // Create a list of Monos for each deletion operation
        List<Mono<Boolean>> deletionMonos = identifierNames.stream()
                .map(identifierName -> 
                    collibraClient.findAssetByIdentifier(identifierName, domainName, communityName)
                        .flatMap(assetId -> {
                            log.debug("Deleting asset with identifier '{}' (UUID: {})", identifierName, assetId);
                            return collibraClient.deleteAsset(assetId);
                        })
                        .defaultIfEmpty(false)
                )
                .toList();

        // Execute all deletions and count successes
        return Mono.zip(deletionMonos, results -> {
            int successCount = 0;
            for (Object result : results) {
                if (result instanceof Boolean && (Boolean) result) {
                    successCount++;
                }
            }
            log.info("Successfully deleted {} out of {} assets from Collibra", successCount, identifierNames.size());
            return successCount;
        }).onErrorResume(e -> {
            log.error("Error during asset deletion: {}", e.getMessage(), e);
            return Mono.just(0);
        });
    }

    /**
     * Converts LocalDateTime to Unix timestamp in milliseconds.
     * Strips the time part by converting to LocalDate, then converts to Instant at UTC midnight.
     * 
     * @param dateTime the LocalDateTime to convert
     * @return Unix timestamp in milliseconds as a String
     */
    private String convertToUnixTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        // Convert to LocalDate (strips time part)
        // Convert back to Instant at UTC midnight
        // Get Unix timestamp in milliseconds
        long timestamp = dateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        return String.valueOf(timestamp);
    }

    /**
     * Adds an attribute to the attributes map.
     */
    private void addAttribute(Map<String, List<CollibraAttributeValue>> attributes, String attributeName, String value) {
        if (value != null && !value.trim().isEmpty()) {
            attributes.put(attributeName, List.of(
                CollibraAttributeValue.builder()
                    .value(value)
                    .build()
            ));
        }
    }

    /**
     * Adds a relation to the relations map.
     */
    private void addRelation(Map<String, List<CollibraRelationTarget>> relations, 
                            String relationKey, 
                            String targetName,
                            String domainName, 
                            String communityName) {
        if (targetName != null && !targetName.trim().isEmpty()) {
            CollibraRelationTarget target = CollibraRelationTarget.builder()
                .name(targetName)
                .domain(CollibraDomain.builder()
                    .name(domainName)
                    .community(CollibraCommunity.builder()
                        .name(communityName)
                        .build())
                    .build())
                .build();
            
            relations.computeIfAbsent(relationKey, k -> new ArrayList<>()).add(target);
        }
    }

    /**
     * Legacy helper method for backward compatibility.
     * @deprecated Use addAttribute(Map, String, String) instead
     */
    @Deprecated
    private void addAttribute(List<CollibraAttribute> attributes, String typeName, String value) {
        if (value != null && !value.trim().isEmpty()) {
            attributes.add(CollibraAttribute.builder()
                    .attributeTypeName(typeName)
                    .value(value)
                    .build());
        }
    }

    /**
     * Capitalizes the field role value for Collibra ingestion.
     * Converts the first character to uppercase and the rest to lowercase.
     * For example: "MEASURE" -> "Measure", "dimension" -> "Dimension"
     *
     * @param fieldRole the field role value from the database
     * @return the capitalized field role, or null if input is null
     */
    private String capitalizeFieldRole(String fieldRole) {
        if (fieldRole == null || fieldRole.isEmpty()) {
            return fieldRole;
        }
        return fieldRole.substring(0, 1).toUpperCase(Locale.ROOT) + fieldRole.substring(1).toLowerCase(Locale.ROOT);
    }
}
