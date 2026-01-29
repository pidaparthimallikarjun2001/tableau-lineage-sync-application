package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.entity.ReportAttribute;
import com.example.tableau.service.CollibraIngestionService;
import com.example.tableau.service.ReportAttributeService;
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
 * Controller for Tableau Report Attribute (Sheet Field Instance) operations.
 */
@RestController
@RequestMapping("/api/report-attributes")
@Tag(name = "Report Attribute", description = "Endpoints for Tableau Report Attributes (Sheet Field Instances)")
public class ReportAttributeController {

    private final ReportAttributeService reportAttributeService;
    private final CollibraIngestionService collibraIngestionService;

    public ReportAttributeController(ReportAttributeService reportAttributeService, CollibraIngestionService collibraIngestionService) {
        this.reportAttributeService = reportAttributeService;
        this.collibraIngestionService = collibraIngestionService;
    }

    @Operation(
        summary = "Get all active report attributes",
        description = "Retrieve all active report attributes (sheet field instances) from the database"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active report attributes")
    })
    @GetMapping
    public ResponseEntity<List<ReportAttribute>> getAllActiveReportAttributes() {
        return ResponseEntity.ok(reportAttributeService.getAllActiveReportAttributes());
    }

    @Operation(
        summary = "Get report attributes by site ID",
        description = "Retrieve all active report attributes for a specific site"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of report attributes in the site")
    })
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<ReportAttribute>> getReportAttributesBySiteId(
            @Parameter(description = "Tableau Site ID")
            @PathVariable("siteId") String siteId) {
        return ResponseEntity.ok(reportAttributeService.getActiveReportAttributesBySiteId(siteId));
    }

    @Operation(
        summary = "Get calculated fields",
        description = "Retrieve all calculated fields with their formulas"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of calculated fields")
    })
    @GetMapping("/calculated")
    public ResponseEntity<List<ReportAttribute>> getCalculatedFields() {
        return ResponseEntity.ok(reportAttributeService.getCalculatedFields());
    }

    @Operation(
        summary = "Get report attribute by ID",
        description = "Retrieve a specific report attribute by its database ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report attribute details"),
        @ApiResponse(responseCode = "404", description = "Report attribute not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReportAttribute> getReportAttributeById(
            @Parameter(description = "Database ID of the report attribute")
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(reportAttributeService.getReportAttributeById(id));
    }

    @Operation(
        summary = "Fetch report attributes from Tableau",
        description = "Retrieve sheet field instances from Tableau GraphQL API for the current site. " +
            "This includes lineage information, source data sources, and calculation logic."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report attributes from Tableau with lineage info")
    })
    @GetMapping("/fetch")
    public ResponseEntity<List<JsonNode>> fetchReportAttributesFromTableau() {
        List<JsonNode> result = reportAttributeService.fetchReportAttributesFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest report attribute metadata",
        description = "Fetch sheet field instances from Tableau GraphQL API and ingest/update in the database. " +
            "This extracts complete lineage information including upstream fields, tables, columns, " +
            "and calculation logic/formulas for calculated fields."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = IngestionResult.class)))
    })
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestReportAttributes() {
        IngestionResult result = reportAttributeService.ingestReportAttributes().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest report attributes to Collibra",
        description = "Ingest all report attributes from the database to Collibra. " +
            "First run: All assets ingested. Subsequent runs: Only NEW, UPDATED, DELETED changes are synchronized."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result",
            content = @Content(schema = @Schema(implementation = CollibraIngestionResult.class)))
    })
    @PostMapping("/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestToCollibra() {
        CollibraIngestionResult result = collibraIngestionService.ingestReportAttributesToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single report attribute to Collibra",
        description = "Ingest a specific report attribute from the database to Collibra"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result"),
        @ApiResponse(responseCode = "404", description = "Report attribute not found")
    })
    @PostMapping("/{id}/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestReportAttributeToCollibra(
            @Parameter(description = "Database ID of the report attribute")
            @PathVariable("id") Long id) {
        CollibraIngestionResult result = collibraIngestionService.ingestReportAttributeToCollibra(id).block();
        return ResponseEntity.ok(result);
    }
}
