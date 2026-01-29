package com.example.tableau.controller;

import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.service.CollibraIngestionService;
import com.example.tableau.service.CollibraRestClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Collibra integration operations.
 * Provides endpoints to ingest Tableau assets from the local database to Collibra.
 */
@RestController
@RequestMapping("/api/collibra")
@Tag(name = "Collibra", description = "Endpoints for Collibra integration and asset ingestion")
public class CollibraController {

    private final CollibraIngestionService ingestionService;
    private final CollibraRestClient collibraClient;

    public CollibraController(CollibraIngestionService ingestionService, CollibraRestClient collibraClient) {
        this.ingestionService = ingestionService;
        this.collibraClient = collibraClient;
    }

    @Operation(
        summary = "Test Collibra connection",
        description = "Test the connection to Collibra API"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connection status")
    })
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Boolean connected = collibraClient.testConnection().block();
        boolean isConfigured = ingestionService.isConfigured();
        
        return ResponseEntity.ok(Map.of(
            "configured", isConfigured,
            "connected", connected != null && connected,
            "message", isConfigured 
                ? (connected != null && connected ? "Successfully connected to Collibra" : "Failed to connect to Collibra")
                : "Collibra integration is not configured. Set collibra.base-url, collibra.username, and collibra.password."
        ));
    }

    @Operation(
        summary = "Ingest all assets to Collibra",
        description = "Ingest all Tableau assets from the database to Collibra. " +
            "First run: All assets are ingested. Subsequent runs: Only NEW, UPDATED, and DELETED changes are synchronized."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = CollibraIngestionResult.class)))
    })
    @PostMapping("/ingest/all")
    public ResponseEntity<CollibraIngestionResult> ingestAll() {
        CollibraIngestionResult result = ingestionService.ingestAllToCollibra().block();
        return ResponseEntity.ok(result);
    }

    // ======================== Server Endpoints ========================

    @Operation(
        summary = "Ingest servers to Collibra",
        description = "Ingest all Tableau servers from the database to Collibra based on status flags"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result")
    })
    @PostMapping("/ingest/servers")
    public ResponseEntity<CollibraIngestionResult> ingestServers() {
        CollibraIngestionResult result = ingestionService.ingestServersToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single server to Collibra",
        description = "Ingest a specific Tableau server to Collibra by its database ID"
    )
    @PostMapping("/ingest/servers/{id}")
    public ResponseEntity<CollibraIngestionResult> ingestServer(
            @Parameter(description = "Database ID of the server")
            @PathVariable Long id) {
        CollibraIngestionResult result = ingestionService.ingestServerToCollibra(id).block();
        return ResponseEntity.ok(result);
    }

    // ======================== Site Endpoints ========================

    @Operation(
        summary = "Ingest sites to Collibra",
        description = "Ingest all Tableau sites from the database to Collibra based on status flags"
    )
    @PostMapping("/ingest/sites")
    public ResponseEntity<CollibraIngestionResult> ingestSites() {
        CollibraIngestionResult result = ingestionService.ingestSitesToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single site to Collibra",
        description = "Ingest a specific Tableau site to Collibra by its database ID"
    )
    @PostMapping("/ingest/sites/{id}")
    public ResponseEntity<CollibraIngestionResult> ingestSite(
            @Parameter(description = "Database ID of the site")
            @PathVariable Long id) {
        CollibraIngestionResult result = ingestionService.ingestSiteToCollibra(id).block();
        return ResponseEntity.ok(result);
    }

    // ======================== Project Endpoints ========================

    @Operation(
        summary = "Ingest projects to Collibra",
        description = "Ingest all Tableau projects from the database to Collibra based on status flags. " +
            "Handles nested/child projects and their parent-child relations."
    )
    @PostMapping("/ingest/projects")
    public ResponseEntity<CollibraIngestionResult> ingestProjects() {
        CollibraIngestionResult result = ingestionService.ingestProjectsToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single project to Collibra",
        description = "Ingest a specific Tableau project to Collibra by its database ID"
    )
    @PostMapping("/ingest/projects/{id}")
    public ResponseEntity<CollibraIngestionResult> ingestProject(
            @Parameter(description = "Database ID of the project")
            @PathVariable Long id) {
        CollibraIngestionResult result = ingestionService.ingestProjectToCollibra(id).block();
        return ResponseEntity.ok(result);
    }

    // ======================== Workbook Endpoints ========================

    @Operation(
        summary = "Ingest workbooks to Collibra",
        description = "Ingest all Tableau workbooks from the database to Collibra based on status flags"
    )
    @PostMapping("/ingest/workbooks")
    public ResponseEntity<CollibraIngestionResult> ingestWorkbooks() {
        CollibraIngestionResult result = ingestionService.ingestWorkbooksToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single workbook to Collibra",
        description = "Ingest a specific Tableau workbook to Collibra by its database ID"
    )
    @PostMapping("/ingest/workbooks/{id}")
    public ResponseEntity<CollibraIngestionResult> ingestWorkbook(
            @Parameter(description = "Database ID of the workbook")
            @PathVariable Long id) {
        CollibraIngestionResult result = ingestionService.ingestWorkbookToCollibra(id).block();
        return ResponseEntity.ok(result);
    }

    // ======================== Worksheet Endpoints ========================

    @Operation(
        summary = "Ingest worksheets to Collibra",
        description = "Ingest all Tableau worksheets from the database to Collibra based on status flags"
    )
    @PostMapping("/ingest/worksheets")
    public ResponseEntity<CollibraIngestionResult> ingestWorksheets() {
        CollibraIngestionResult result = ingestionService.ingestWorksheetsToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single worksheet to Collibra",
        description = "Ingest a specific Tableau worksheet to Collibra by its database ID"
    )
    @PostMapping("/ingest/worksheets/{id}")
    public ResponseEntity<CollibraIngestionResult> ingestWorksheet(
            @Parameter(description = "Database ID of the worksheet")
            @PathVariable Long id) {
        CollibraIngestionResult result = ingestionService.ingestWorksheetToCollibra(id).block();
        return ResponseEntity.ok(result);
    }

    // ======================== DataSource Endpoints ========================

    @Operation(
        summary = "Ingest data sources to Collibra",
        description = "Ingest all Tableau data sources from the database to Collibra based on status flags"
    )
    @PostMapping("/ingest/datasources")
    public ResponseEntity<CollibraIngestionResult> ingestDataSources() {
        CollibraIngestionResult result = ingestionService.ingestDataSourcesToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single data source to Collibra",
        description = "Ingest a specific Tableau data source to Collibra by its database ID"
    )
    @PostMapping("/ingest/datasources/{id}")
    public ResponseEntity<CollibraIngestionResult> ingestDataSource(
            @Parameter(description = "Database ID of the data source")
            @PathVariable Long id) {
        CollibraIngestionResult result = ingestionService.ingestDataSourceToCollibra(id).block();
        return ResponseEntity.ok(result);
    }

    // ======================== Report Attribute Endpoints ========================

    @Operation(
        summary = "Ingest report attributes to Collibra",
        description = "Ingest all Tableau report attributes from the database to Collibra based on status flags"
    )
    @PostMapping("/ingest/report-attributes")
    public ResponseEntity<CollibraIngestionResult> ingestReportAttributes() {
        CollibraIngestionResult result = ingestionService.ingestReportAttributesToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single report attribute to Collibra",
        description = "Ingest a specific Tableau report attribute to Collibra by its database ID"
    )
    @PostMapping("/ingest/report-attributes/{id}")
    public ResponseEntity<CollibraIngestionResult> ingestReportAttribute(
            @Parameter(description = "Database ID of the report attribute")
            @PathVariable Long id) {
        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(id).block();
        return ResponseEntity.ok(result);
    }

    // ======================== Site-Level Ingestion Endpoints ========================

    @Operation(
        summary = "Ingest all assets for a site to Collibra",
        description = "Ingest all Tableau assets (projects, workbooks, worksheets, data sources, report attributes) " +
            "for a specific site to Collibra. This reduces load on Collibra by processing assets site by site."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result for all assets in the site",
            content = @Content(schema = @Schema(implementation = CollibraIngestionResult.class)))
    })
    @PostMapping("/ingest/sites/{siteId}/all")
    public ResponseEntity<CollibraIngestionResult> ingestAllBySite(
            @Parameter(description = "Tableau Site ID (asset ID)")
            @PathVariable String siteId) {
        CollibraIngestionResult result = ingestionService.ingestAllBySiteToCollibra(siteId).block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest projects for a site to Collibra",
        description = "Ingest all Tableau projects for a specific site to Collibra based on status flags. " +
            "This reduces load on Collibra by processing assets site by site."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result for projects in the site")
    })
    @PostMapping("/ingest/sites/{siteId}/projects")
    public ResponseEntity<CollibraIngestionResult> ingestProjectsBySite(
            @Parameter(description = "Tableau Site ID (asset ID)")
            @PathVariable String siteId) {
        CollibraIngestionResult result = ingestionService.ingestProjectsBySiteToCollibra(siteId).block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest workbooks for a site to Collibra",
        description = "Ingest all Tableau workbooks for a specific site to Collibra based on status flags. " +
            "This reduces load on Collibra by processing assets site by site."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result for workbooks in the site")
    })
    @PostMapping("/ingest/sites/{siteId}/workbooks")
    public ResponseEntity<CollibraIngestionResult> ingestWorkbooksBySite(
            @Parameter(description = "Tableau Site ID (asset ID)")
            @PathVariable String siteId) {
        CollibraIngestionResult result = ingestionService.ingestWorkbooksBySiteToCollibra(siteId).block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest worksheets for a site to Collibra",
        description = "Ingest all Tableau worksheets for a specific site to Collibra based on status flags. " +
            "This reduces load on Collibra by processing assets site by site."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result for worksheets in the site")
    })
    @PostMapping("/ingest/sites/{siteId}/worksheets")
    public ResponseEntity<CollibraIngestionResult> ingestWorksheetsBySite(
            @Parameter(description = "Tableau Site ID (asset ID)")
            @PathVariable String siteId) {
        CollibraIngestionResult result = ingestionService.ingestWorksheetsBySiteToCollibra(siteId).block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest data sources for a site to Collibra",
        description = "Ingest all Tableau data sources for a specific site to Collibra based on status flags. " +
            "This reduces load on Collibra by processing assets site by site."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result for data sources in the site")
    })
    @PostMapping("/ingest/sites/{siteId}/datasources")
    public ResponseEntity<CollibraIngestionResult> ingestDataSourcesBySite(
            @Parameter(description = "Tableau Site ID (asset ID)")
            @PathVariable String siteId) {
        CollibraIngestionResult result = ingestionService.ingestDataSourcesBySiteToCollibra(siteId).block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest report attributes for a site to Collibra",
        description = "Ingest all Tableau report attributes for a specific site to Collibra based on status flags. " +
            "This reduces load on Collibra by processing assets site by site."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result for report attributes in the site")
    })
    @PostMapping("/ingest/sites/{siteId}/report-attributes")
    public ResponseEntity<CollibraIngestionResult> ingestReportAttributesBySite(
            @Parameter(description = "Tableau Site ID (asset ID)")
            @PathVariable String siteId) {
        CollibraIngestionResult result = ingestionService.ingestReportAttributesBySiteToCollibra(siteId).block();
        return ResponseEntity.ok(result);
    }
}
