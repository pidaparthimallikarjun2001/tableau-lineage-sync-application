package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.entity.TableauWorksheet;
import com.example.tableau.service.CollibraIngestionService;
import com.example.tableau.service.WorksheetService;
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
 * Controller for Tableau Worksheet operations.
 */
@RestController
@RequestMapping("/api/worksheets")
@Tag(name = "Worksheet", description = "Endpoints for Tableau Worksheet (Sheet) metadata")
public class WorksheetController {

    private final WorksheetService worksheetService;
    private final CollibraIngestionService collibraIngestionService;

    public WorksheetController(WorksheetService worksheetService, CollibraIngestionService collibraIngestionService) {
        this.worksheetService = worksheetService;
        this.collibraIngestionService = collibraIngestionService;
    }

    @Operation(
        summary = "Get all active worksheets",
        description = "Retrieve all active Tableau worksheets from the database"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active worksheets")
    })
    @GetMapping
    public ResponseEntity<List<TableauWorksheet>> getAllActiveWorksheets() {
        return ResponseEntity.ok(worksheetService.getAllActiveWorksheets());
    }

    @Operation(
        summary = "Get worksheets by site ID",
        description = "Retrieve all active worksheets for a specific site"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of worksheets in the site")
    })
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<TableauWorksheet>> getWorksheetsBySiteId(
            @Parameter(description = "Tableau Site ID")
            @PathVariable("siteId") String siteId) {
        return ResponseEntity.ok(worksheetService.getActiveWorksheetsBySiteId(siteId));
    }

    @Operation(
        summary = "Get worksheet by ID",
        description = "Retrieve a specific Tableau worksheet by its database ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Worksheet details"),
        @ApiResponse(responseCode = "404", description = "Worksheet not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TableauWorksheet> getWorksheetById(
            @Parameter(description = "Database ID of the worksheet")
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(worksheetService.getWorksheetById(id));
    }

    @Operation(
        summary = "Fetch worksheets from Tableau",
        description = "Retrieve worksheets from Tableau GraphQL API for the current site (does not persist to database)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Worksheets from Tableau")
    })
    @GetMapping("/fetch")
    public ResponseEntity<List<JsonNode>> fetchWorksheetsFromTableau() {
        List<JsonNode> result = worksheetService.fetchWorksheetsFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest worksheet metadata",
        description = "Fetch worksheets from Tableau GraphQL API and ingest/update in the database. " +
            "This will compare with existing data and mark records as NEW, UPDATED, or DELETED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = IngestionResult.class)))
    })
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestWorksheets() {
        IngestionResult result = worksheetService.ingestWorksheets().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Soft delete worksheet and children",
        description = "Soft delete a worksheet and all its child assets (report attributes)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Worksheet and children soft deleted"),
        @ApiResponse(responseCode = "404", description = "Worksheet not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteWorksheet(
            @Parameter(description = "Database ID of the worksheet to delete")
            @PathVariable("id") Long id) {
        worksheetService.softDeleteWorksheetAndChildren(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Ingest worksheets to Collibra",
        description = "Ingest all worksheets from the database to Collibra. " +
            "First run: All assets ingested. Subsequent runs: Only NEW, UPDATED, DELETED changes are synchronized."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result",
            content = @Content(schema = @Schema(implementation = CollibraIngestionResult.class)))
    })
    @PostMapping("/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestToCollibra() {
        CollibraIngestionResult result = collibraIngestionService.ingestWorksheetsToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single worksheet to Collibra",
        description = "Ingest a specific worksheet from the database to Collibra"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result"),
        @ApiResponse(responseCode = "404", description = "Worksheet not found")
    })
    @PostMapping("/{id}/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestWorksheetToCollibra(
            @Parameter(description = "Database ID of the worksheet")
            @PathVariable("id") Long id) {
        CollibraIngestionResult result = collibraIngestionService.ingestWorksheetToCollibra(id).block();
        return ResponseEntity.ok(result);
    }
}
