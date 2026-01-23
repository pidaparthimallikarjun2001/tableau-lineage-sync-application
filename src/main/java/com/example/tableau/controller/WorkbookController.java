package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauWorkbook;
import com.example.tableau.service.WorkbookService;
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
 * Controller for Tableau Workbook operations.
 */
@RestController
@RequestMapping("/api/workbooks")
@Tag(name = "Workbook", description = "Endpoints for Tableau Workbook metadata")
public class WorkbookController {

    private final WorkbookService workbookService;

    public WorkbookController(WorkbookService workbookService) {
        this.workbookService = workbookService;
    }

    @Operation(
        summary = "Get all active workbooks",
        description = "Retrieve all active Tableau workbooks from the database"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active workbooks")
    })
    @GetMapping
    public ResponseEntity<List<TableauWorkbook>> getAllActiveWorkbooks() {
        return ResponseEntity.ok(workbookService.getAllActiveWorkbooks());
    }

    @Operation(
        summary = "Get workbooks by site ID",
        description = "Retrieve all active workbooks for a specific site"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of workbooks in the site")
    })
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<TableauWorkbook>> getWorkbooksBySiteId(
            @Parameter(description = "Tableau Site ID")
            @PathVariable String siteId) {
        return ResponseEntity.ok(workbookService.getActiveWorkbooksBySiteId(siteId));
    }

    @Operation(
        summary = "Get workbook by ID",
        description = "Retrieve a specific Tableau workbook by its database ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Workbook details"),
        @ApiResponse(responseCode = "404", description = "Workbook not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TableauWorkbook> getWorkbookById(
            @Parameter(description = "Database ID of the workbook")
            @PathVariable Long id) {
        return ResponseEntity.ok(workbookService.getWorkbookById(id));
    }

    @Operation(
        summary = "Fetch workbooks from Tableau",
        description = "Retrieve workbooks from Tableau GraphQL API for the current site (does not persist to database)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Workbooks from Tableau")
    })
    @GetMapping("/fetch")
    public ResponseEntity<List<JsonNode>> fetchWorkbooksFromTableau() {
        List<JsonNode> result = workbookService.fetchWorkbooksFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest workbook metadata",
        description = "Fetch workbooks from Tableau GraphQL API and ingest/update in the database. " +
            "This will compare with existing data and mark records as NEW, UPDATED, or DELETED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = IngestionResult.class)))
    })
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestWorkbooks() {
        IngestionResult result = workbookService.ingestWorkbooks().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Soft delete workbook and children",
        description = "Soft delete a workbook and all its child assets (worksheets, report attributes, embedded data sources)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Workbook and children soft deleted"),
        @ApiResponse(responseCode = "404", description = "Workbook not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteWorkbook(
            @Parameter(description = "Database ID of the workbook to delete")
            @PathVariable Long id) {
        workbookService.softDeleteWorkbookAndChildren(id);
        return ResponseEntity.ok().build();
    }
}
