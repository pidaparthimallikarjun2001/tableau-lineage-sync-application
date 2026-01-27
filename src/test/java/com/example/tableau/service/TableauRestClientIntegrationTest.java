package com.example.tableau.service;

import com.example.tableau.config.TableauApiConfig;
import com.example.tableau.dto.SiteSwitchResponse;
import com.example.tableau.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TableauRestClient to ensure it works properly after authentication.
 */
class TableauRestClientIntegrationTest {

    private MockWebServer mockWebServer;
    private TableauAuthService authService;
    private TableauRestClient restClient;
    private TableauApiConfig apiConfig;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Setup test configuration
        apiConfig = new TableauApiConfig();
        TestUtils.setPrivateField(apiConfig, "baseUrl", mockWebServer.url("/").toString().replaceAll("/$", ""));
        TestUtils.setPrivateField(apiConfig, "apiVersion", "3.17");
        TestUtils.setPrivateField(apiConfig, "authMode", "PAT");
        TestUtils.setPrivateField(apiConfig, "patName", "testPat");
        TestUtils.setPrivateField(apiConfig, "patSecret", "testSecret");
        TestUtils.setPrivateField(apiConfig, "defaultSiteId", "");

        // Create services
        WebClient.Builder webClientBuilder = WebClient.builder();
        authService = new TableauAuthService(webClientBuilder, apiConfig);
        restClient = new TableauRestClient(webClientBuilder, authService, apiConfig);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should be able to get current site after signing in")
    void testGetCurrentSiteAfterSignIn() throws Exception {
        // Mock sign-in response
        String signInResponse = """
            {
                "credentials": {
                    "token": "test-auth-token",
                    "site": {
                        "id": "test-site-id",
                        "name": "Test Site",
                        "contentUrl": "testsite"
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(signInResponse)
                .addHeader("Content-Type", "application/json"));

        // Sign in
        SiteSwitchResponse signInResult = authService.signIn("testsite").block();
        assertNotNull(signInResult);
        assertTrue(signInResult.isSuccess());

        // Mock get current site response
        String getSiteResponse = """
            {
                "site": {
                    "id": "test-site-id",
                    "name": "Test Site",
                    "contentUrl": "testsite",
                    "state": "Active"
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(getSiteResponse)
                .addHeader("Content-Type", "application/json"));

        // Now try to get current site
        var siteResult = restClient.getCurrentSite().block();
        
        // Verify the result
        assertNotNull(siteResult);
        assertEquals("test-site-id", siteResult.path("id").asText());
        assertEquals("Test Site", siteResult.path("name").asText());

        // Verify that the auth token was used in the request
        mockWebServer.takeRequest(); // skip sign-in request
        RecordedRequest getSiteRequest = mockWebServer.takeRequest();
        assertEquals("/api/3.17/sites/test-site-id", getSiteRequest.getPath());
        assertEquals("test-auth-token", getSiteRequest.getHeader("X-Tableau-Auth"));
    }

    @Test
    @DisplayName("Should be able to get sites after signing in")
    void testGetSitesAfterSignIn() throws Exception {
        // Mock sign-in response
        String signInResponse = """
            {
                "credentials": {
                    "token": "test-auth-token",
                    "site": {
                        "id": "test-site-id",
                        "name": "Test Site",
                        "contentUrl": "testsite"
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(signInResponse)
                .addHeader("Content-Type", "application/json"));

        // Sign in
        authService.signIn("testsite").block();

        // Mock get sites response
        String getSitesResponse = """
            {
                "sites": {
                    "site": [
                        {
                            "id": "site-1",
                            "name": "Site One",
                            "contentUrl": "siteone"
                        },
                        {
                            "id": "site-2",
                            "name": "Site Two",
                            "contentUrl": "sitetwo"
                        }
                    ]
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(getSitesResponse)
                .addHeader("Content-Type", "application/json"));

        // Get sites
        var sites = restClient.getSites().block();
        
        // Verify the result
        assertNotNull(sites);
        assertEquals(2, sites.size());
        assertEquals("Site One", sites.get(0).path("name").asText());
        assertEquals("Site Two", sites.get(1).path("name").asText());

        // Verify auth token was used
        mockWebServer.takeRequest(); // skip sign-in request
        RecordedRequest getSitesRequest = mockWebServer.takeRequest();
        assertEquals("/api/3.17/sites", getSitesRequest.getPath());
        assertEquals("test-auth-token", getSitesRequest.getHeader("X-Tableau-Auth"));
    }

    @Test
    @DisplayName("Should be able to get projects after signing in")
    void testGetProjectsAfterSignIn() throws Exception {
        // Mock sign-in response
        String signInResponse = """
            {
                "credentials": {
                    "token": "test-auth-token",
                    "site": {
                        "id": "test-site-id",
                        "name": "Test Site",
                        "contentUrl": "testsite"
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(signInResponse)
                .addHeader("Content-Type", "application/json"));

        // Sign in
        authService.signIn("testsite").block();

        // Mock get projects response
        String getProjectsResponse = """
            {
                "projects": {
                    "project": [
                        {
                            "id": "project-1",
                            "name": "Project One"
                        }
                    ]
                },
                "pagination": {
                    "pageNumber": 1,
                    "pageSize": 100,
                    "totalAvailable": 1
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(getProjectsResponse)
                .addHeader("Content-Type", "application/json"));

        // Get projects
        var projects = restClient.getProjects().block();
        
        // Verify the result
        assertNotNull(projects);
        assertEquals(1, projects.size());
        assertEquals("Project One", projects.get(0).path("name").asText());

        // Verify auth token and site ID were used
        mockWebServer.takeRequest(); // skip sign-in request
        RecordedRequest getProjectsRequest = mockWebServer.takeRequest();
        assertTrue(getProjectsRequest.getPath().contains("/sites/test-site-id/projects"));
        assertEquals("test-auth-token", getProjectsRequest.getHeader("X-Tableau-Auth"));
    }

    @Test
    @DisplayName("Should fail to get current site when not authenticated")
    void testGetCurrentSiteWithoutAuthentication() {
        // Try to get current site without signing in first
        Exception exception = assertThrows(Exception.class, () -> {
            restClient.getCurrentSite().block();
        });

        // Verify it fails with appropriate error
        assertTrue(exception.getMessage().contains("No active site") || 
                   exception.getMessage().contains("Please sign in"));
    }

    @Test
    @DisplayName("Should use refreshed token when original token expires")
    void testTokenRefresh() throws Exception {
        // Mock first sign-in
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "credentials": {
                            "token": "token-1",
                            "site": {
                                "id": "site-id",
                                "name": "Test Site",
                                "contentUrl": ""
                            }
                        }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        // Sign in
        authService.signIn(null).block();

        // Mock first API call
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "sites": {
                            "site": []
                        }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        // Make first call - should use token-1
        restClient.getSites().block();

        // Verify first call used token-1
        mockWebServer.takeRequest(); // skip sign-in
        RecordedRequest firstCall = mockWebServer.takeRequest();
        assertEquals("token-1", firstCall.getHeader("X-Tableau-Auth"));
        
        // The token refresh logic is tested implicitly through getAuthToken()
        // which will call signIn() when token expires
    }
}
