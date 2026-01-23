package com.example.tableau.controller;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.dto.SiteSwitchRequest;
import com.example.tableau.dto.SiteSwitchResponse;
import com.example.tableau.service.TableauAuthService;
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

import java.util.Map;

/**
 * Controller for Tableau authentication and site management.
 */
@RestController
@RequestMapping("/api/tableau")
@Tag(name = "Tableau Site Management", description = "Endpoints for Tableau authentication and site switching")
public class TableauSiteController {

    private final TableauAuthService authService;

    public TableauSiteController(TableauAuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "Get current site info",
        description = "Get information about the currently active Tableau site"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current site information"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/site/current")
    public ResponseEntity<Map<String, String>> getCurrentSite() {
        String siteId = authService.getCurrentSiteId();
        String siteName = authService.getCurrentSiteName();
        String contentUrl = authService.getCurrentSiteContentUrl();
        
        if (siteId == null) {
            return ResponseEntity.ok(Map.of(
                "status", "not_authenticated",
                "message", "Not currently authenticated to any Tableau site"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "siteId", siteId,
            "siteName", siteName != null ? siteName : "",
            "contentUrl", contentUrl != null ? contentUrl : ""
        ));
    }

    @Operation(
        summary = "Switch Tableau site",
        description = "Switch to a different Tableau site using the site's content URL. " +
            "This will authenticate to the new site and update the session context. " +
            "All subsequent API calls will operate within the context of the new site."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully switched to new site",
            content = @Content(schema = @Schema(implementation = SiteSwitchResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Authentication failed"),
        @ApiResponse(responseCode = "404", description = "Site not found")
    })
    @PostMapping("/site/switch")
    public ResponseEntity<SiteSwitchResponse> switchSite(
            @Parameter(description = "Site switch request with content URL")
            @RequestBody SiteSwitchRequest request) {
        
        if (request.getSiteContentUrl() == null || request.getSiteContentUrl().isEmpty()) {
            return ResponseEntity.badRequest().body(
                SiteSwitchResponse.builder()
                    .success(false)
                    .message("Site content URL is required")
                    .build()
            );
        }
        
        SiteSwitchResponse response = authService.switchSite(request.getSiteContentUrl()).block();
        
        if (response != null && response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(
                response != null ? response : 
                    SiteSwitchResponse.builder()
                        .success(false)
                        .message("Failed to switch site")
                        .build()
            );
        }
    }

    @Operation(
        summary = "Authenticate to Tableau",
        description = "Sign in to Tableau and optionally specify a site. " +
            "If no site is specified, the default site from configuration will be used."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
        @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    @PostMapping("/auth/signin")
    public ResponseEntity<SiteSwitchResponse> signIn(
            @Parameter(description = "Optional site content URL to sign in to")
            @RequestParam(required = false) String siteContentUrl) {
        
        SiteSwitchResponse response = authService.signIn(siteContentUrl).block();
        
        if (response != null && response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(
                response != null ? response :
                    SiteSwitchResponse.builder()
                        .success(false)
                        .message("Failed to authenticate")
                        .build()
            );
        }
    }

    @Operation(
        summary = "Sign out from Tableau",
        description = "Sign out from the current Tableau session"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully signed out")
    })
    @PostMapping("/auth/signout")
    public ResponseEntity<Map<String, String>> signOut() {
        authService.signOut().block();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Successfully signed out"
        ));
    }
}
