package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.entity.TableauDataSource;
import com.example.tableau.enums.SourceType;
import com.example.tableau.service.CollibraIngestionService;
import com.example.tableau.service.DataSourceService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Tableau Data Source operations.
 */
@RestController
@RequestMapping("/api/datasources")
@Tag(name = "Data Source", description = "Endpoints for Tableau Data Source metadata (Direct and Custom SQL)")
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final CollibraIngestionService collibraIngestionService;

    public DataSourceController(DataSourceService dataSourceService, CollibraIngestionService collibraIngestionService) {
        this.dataSourceService = dataSourceService;
        this.collibraIngestionService = collibraIngestionService;
    }

    @Operation(
        summary = "Get all active data sources",
        description = "Retrieve all active data sources from the database"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active data sources")
    })
    @GetMapping
    public ResponseEntity<List<TableauDataSource>> getAllActiveDataSources() {
        return ResponseEntity.ok(dataSourceService.getAllActiveDataSources());
    }

    @Operation(
        summary = "Get data sources by site ID",
        description = "Retrieve all active data sources for a specific site"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of data sources in the site")
    })
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<TableauDataSource>> getDataSourcesBySiteId(
            @Parameter(description = "Tableau Site ID")
            @PathVariable("siteId") String siteId) {
        return ResponseEntity.ok(dataSourceService.getActiveDataSourcesBySiteId(siteId));
    }

    @Operation(
        summary = "Get data sources by type",
        description = "Retrieve data sources filtered by source type (DIRECT_IMPORT, CUSTOM_SQL, PUBLISHED, etc.)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of data sources of the specified type")
    })
    @GetMapping("/type/{sourceType}")
    public ResponseEntity<List<TableauDataSource>> getDataSourcesByType(
            @Parameter(description = "Source type (DIRECT_IMPORT, CUSTOM_SQL, PUBLISHED, FILE_BASED, OTHER)")
            @PathVariable("sourceType") SourceType sourceType) {
        return ResponseEntity.ok(dataSourceService.getDataSourcesByType(sourceType));
    }

    @Operation(
        summary = "Get published data sources",
        description = "Retrieve all published (shared) data sources"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of published data sources")
    })
    @GetMapping("/published")
    public ResponseEntity<List<TableauDataSource>> getPublishedDataSources() {
        return ResponseEntity.ok(dataSourceService.getPublishedDataSources());
    }

    @Operation(
        summary = "Get certified data sources",
        description = "Retrieve all certified data sources"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of certified data sources")
    })
    @GetMapping("/certified")
    public ResponseEntity<List<TableauDataSource>> getCertifiedDataSources() {
        return ResponseEntity.ok(dataSourceService.getCertifiedDataSources());
    }

    @Operation(
        summary = "Get data source by ID",
        description = "Retrieve a specific data source by its database ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data source details"),
        @ApiResponse(responseCode = "404", description = "Data source not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TableauDataSource> getDataSourceById(
            @Parameter(description = "Database ID of the data source")
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(dataSourceService.getDataSourceById(id));
    }

    @Operation(
        summary = "Fetch published data sources from Tableau",
        description = "Retrieve published data sources from Tableau GraphQL API for the current site"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Published data sources from Tableau")
    })
    @GetMapping("/fetch/published")
    public ResponseEntity<List<JsonNode>> fetchPublishedDataSourcesFromTableau() {
        List<JsonNode> result = dataSourceService.fetchPublishedDataSourcesFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Fetch embedded data sources from Tableau",
        description = "Retrieve embedded (workbook-specific) data sources from Tableau GraphQL API"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Embedded data sources from Tableau")
    })
    @GetMapping("/fetch/embedded")
    public ResponseEntity<List<JsonNode>> fetchEmbeddedDataSourcesFromTableau() {
        List<JsonNode> result = dataSourceService.fetchEmbeddedDataSourcesFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Fetch custom SQL tables from Tableau",
        description = "Retrieve custom SQL tables from Tableau GraphQL API. " +
            "These contain the SQL queries used to define data sources."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Custom SQL tables from Tableau with queries")
    })
    @GetMapping("/fetch/custom-sql")
    public ResponseEntity<List<JsonNode>> fetchCustomSQLTablesFromTableau() {
        List<JsonNode> result = dataSourceService.fetchCustomSQLTablesFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest all data source metadata",
        description = "Fetch all data sources (published, embedded, and custom SQL) from Tableau and ingest/update in the database. " +
            "This includes connection details, table names, custom SQL queries, upstream tables, and calculated fields."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = IngestionResult.class)))
    })
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestDataSources() {
        IngestionResult result = dataSourceService.ingestDataSources().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest data sources to Collibra",
        description = "Ingest all data sources from the database to Collibra. " +
            "First run: All assets ingested. Subsequent runs: Only NEW, UPDATED, DELETED changes are synchronized."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result",
            content = @Content(schema = @Schema(implementation = CollibraIngestionResult.class)))
    })
    @PostMapping("/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestToCollibra() {
        CollibraIngestionResult result = collibraIngestionService.ingestDataSourcesToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single data source to Collibra",
        description = "Ingest a specific data source from the database to Collibra"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result"),
        @ApiResponse(responseCode = "404", description = "Data source not found")
    })
    @PostMapping("/{id}/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestDataSourceToCollibra(
            @Parameter(description = "Database ID of the data source")
            @PathVariable("id") Long id) {
        CollibraIngestionResult result = collibraIngestionService.ingestDataSourceToCollibra(id).block();
        return ResponseEntity.ok(result);
    }
}
