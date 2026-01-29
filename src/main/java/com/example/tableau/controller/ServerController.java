package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.entity.TableauServer;
import com.example.tableau.service.CollibraIngestionService;
import com.example.tableau.service.ServerService;
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
 * Controller for Tableau Server operations.
 */
@RestController
@RequestMapping("/api/servers")
@Tag(name = "Server", description = "Endpoints for Tableau Server metadata")
public class ServerController {

    private final ServerService serverService;
    private final CollibraIngestionService collibraIngestionService;

    public ServerController(ServerService serverService, CollibraIngestionService collibraIngestionService) {
        this.serverService = serverService;
        this.collibraIngestionService = collibraIngestionService;
    }

    @Operation(
        summary = "Get all active servers",
        description = "Retrieve all active Tableau servers from the database"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active servers",
            content = @Content(schema = @Schema(implementation = TableauServer.class)))
    })
    @GetMapping
    public ResponseEntity<List<TableauServer>> getAllActiveServers() {
        return ResponseEntity.ok(serverService.getAllActiveServers());
    }

    @Operation(
        summary = "Get server by ID",
        description = "Retrieve a specific Tableau server by its database ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Server details"),
        @ApiResponse(responseCode = "404", description = "Server not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TableauServer> getServerById(
            @Parameter(description = "Database ID of the server")
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(serverService.getServerById(id));
    }

    @Operation(
        summary = "Fetch server info from Tableau",
        description = "Retrieve server information directly from Tableau API (does not persist to database)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Server info from Tableau")
    })
    @GetMapping("/fetch")
    public ResponseEntity<JsonNode> fetchServerFromTableau() {
        JsonNode result = serverService.fetchServerInfoFromTableau().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest server metadata",
        description = "Fetch server metadata from Tableau and ingest/update in the database. " +
            "This will compare with existing data and mark records as NEW, UPDATED, or DELETED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ingestion result",
            content = @Content(schema = @Schema(implementation = IngestionResult.class)))
    })
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestServer() {
        IngestionResult result = serverService.ingestServer().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Soft delete server and children",
        description = "Soft delete a server and all its child assets (sites, projects, workbooks, etc.)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Server and children soft deleted"),
        @ApiResponse(responseCode = "404", description = "Server not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteServer(
            @Parameter(description = "Database ID of the server to delete")
            @PathVariable("id") Long id) {
        serverService.softDeleteServerAndChildren(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Ingest servers to Collibra",
        description = "Ingest all servers from the database to Collibra. " +
            "First run: All assets ingested. Subsequent runs: Only NEW, UPDATED, DELETED changes are synchronized."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result",
            content = @Content(schema = @Schema(implementation = CollibraIngestionResult.class)))
    })
    @PostMapping("/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestToCollibra() {
        CollibraIngestionResult result = collibraIngestionService.ingestServersToCollibra().block();
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest a single server to Collibra",
        description = "Ingest a specific server from the database to Collibra"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collibra ingestion result"),
        @ApiResponse(responseCode = "404", description = "Server not found")
    })
    @PostMapping("/{id}/ingest-to-collibra")
    public ResponseEntity<CollibraIngestionResult> ingestServerToCollibra(
            @Parameter(description = "Database ID of the server")
            @PathVariable("id") Long id) {
        CollibraIngestionResult result = collibraIngestionService.ingestServerToCollibra(id).block();
        return ResponseEntity.ok(result);
    }
}
