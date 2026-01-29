package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.entity.TableauProject;
import com.example.tableau.service.CollibraIngestionService;
import com.example.tableau.service.ProjectService;
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
 * Controller for Tableau Project operations.
 */
@RestController
@RequestMapping("/api/projects")
@Tag(name = "Project", description = "Endpoints for Tableau Project metadata")
public class ProjectController {

    private final ProjectService projectService;
    private final CollibraIngestionService collibraIngestionService;

    public ProjectController(ProjectService projectService, CollibraIngestionService collibraIngestionService) {
        this.projectService = projectService;
        this.collibraIngestionService = collibraIngestionService;
    }

    @Operation(
        summary = "Get all active projects",
        description = "Retrieve all active Tableau projects from the database"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active projects")
    })
    @GetMapping
    public ResponseEntity<List<TableauProject>> getAllActiveProjects() {
        return ResponseEntity.ok(projectService.getAllActiveProjects());
    }

    @Operation(
        summary = "Get projects by site ID",
        description = "Retrieve all active projects for a specific site"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of projects in the site")
    })
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<TableauProject>> getProjectsBySiteId(
            @Parameter(description = "Tableau Site ID")
            @PathVariable("siteId") String siteId) {
        return ResponseEntity.ok(projectService.getActiveProjectsBySiteId(siteId));
    }

    @Operation(
        summary = "Get project by ID",
        description = "Retrieve a specific Tableau project by its database ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project details"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TableauProject> getProjectById(
            @Parameter(description = "Database ID of the project")
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @Operation(
        summary = "Fetch projects from Tableau",
        description = "Retrieve projects from Tableau REST API for the current site (does not persist to database)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projects from Tableau")
    })
    @GetMapping("/fetch")
    public ResponseEntity<List<JsonNode>> fetchProjectsFromTableau() {
        List<JsonNode> result = projectService.fetchProjectsFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest project metadata",
        description = "Fetch projects from Tableau REST API and ingest/update in the database. " +
            "This will compare with existing data and mark records as NEW, UPDATED, or DELETED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = IngestionResult.class)))
    })
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestProjects() {
        IngestionResult result = projectService.ingestProjects().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Soft delete project and children",
        description = "Soft delete a project and all its child assets (workbooks, worksheets, etc.)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project and children soft deleted"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteProject(
            @Parameter(description = "Database ID of the project to delete")
            @PathVariable("id") Long id) {
        projectService.softDeleteProjectAndChildren(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Ingest projects to Collibra",
        description = "Ingest all projects from the database to Collibra. " +
            "Handles nested/child projects and their parent-child relations. " +
            "First run: All assets ingested. Subsequent runs: Only NEW, UPDATED, DELETED changes are synchronized."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result",
            content = @Content(schema = @Schema(implementation = CollibraIngestionResult.class)))
    })
    @PostMapping("/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestToCollibra() {
        CollibraIngestionResult result = collibraIngestionService.ingestProjectsToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single project to Collibra",
        description = "Ingest a specific project from the database to Collibra"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    @PostMapping("/{id}/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestProjectToCollibra(
            @Parameter(description = "Database ID of the project")
            @PathVariable("id") Long id) {
        CollibraIngestionResult result = collibraIngestionService.ingestProjectToCollibra(id).block();
        return ResponseEntity.ok(result);
    }
}
