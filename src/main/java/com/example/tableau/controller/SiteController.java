package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.entity.TableauSite;
import com.example.tableau.exception.ResourceNotFoundException;
import com.example.tableau.service.SiteService;
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
 * Controller for Tableau Site operations.
 */
@RestController
@RequestMapping("/api/sites")
@Tag(name = "Site", description = "Endpoints for Tableau Site metadata")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @Operation(
        summary = "Get all active sites",
        description = "Retrieve all active Tableau sites from the database"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active sites")
    })
    @GetMapping
    public ResponseEntity<List<TableauSite>> getAllActiveSites() {
        return ResponseEntity.ok(siteService.getAllActiveSites());
    }

    @Operation(
        summary = "Get site by database ID",
        description = "Retrieve a specific Tableau site by its database ID (primary key). " +
            "Note: This is NOT the Tableau asset ID (LUID). " +
            "Use GET /api/sites/asset/{assetId} to retrieve by Tableau asset ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Site details"),
        @ApiResponse(responseCode = "404", description = "Site not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TableauSite> getSiteById(
            @Parameter(description = "Database primary key ID (e.g., 1, 2, 3). NOT the Tableau asset ID (LUID).")
            @PathVariable Long id) {
        return ResponseEntity.ok(siteService.getSiteById(id));
    }

    @Operation(
        summary = "Get site by Tableau asset ID",
        description = "Retrieve a specific Tableau site by its Tableau asset ID (LUID). " +
            "The asset ID is the unique identifier from Tableau (e.g., 'a1b2c3d4-e5f6-7890-abcd-ef1234567890')."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Site details"),
        @ApiResponse(responseCode = "404", description = "Site not found with the specified asset ID")
    })
    @GetMapping("/asset/{assetId}")
    public ResponseEntity<TableauSite> getSiteByAssetId(
            @Parameter(description = "Tableau asset ID (LUID) of the site")
            @PathVariable String assetId) {
        return ResponseEntity.ok(
            siteService.getSiteByAssetId(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Site with asset ID", assetId))
        );
    }

    @Operation(
        summary = "Fetch sites from Tableau",
        description = "Retrieve all sites from Tableau REST API (does not persist to database)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sites from Tableau")
    })
    @GetMapping("/fetch")
    public ResponseEntity<List<JsonNode>> fetchSitesFromTableau() {
        List<JsonNode> result = siteService.fetchSitesFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest site metadata",
        description = "Fetch sites from Tableau and ingest/update in the database. " +
            "This will compare with existing data and mark records as NEW, UPDATED, or DELETED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = IngestionResult.class)))
    })
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestSites() {
        IngestionResult result = siteService.ingestSites().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Soft delete site and children",
        description = "Soft delete a site and all its child assets (projects, workbooks, worksheets, etc.) by database ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Site and children soft deleted"),
        @ApiResponse(responseCode = "404", description = "Site not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteSite(
            @Parameter(description = "Database primary key ID of the site to delete")
            @PathVariable Long id) {
        siteService.softDeleteSiteAndChildren(id);
        return ResponseEntity.ok().build();
    }
}
