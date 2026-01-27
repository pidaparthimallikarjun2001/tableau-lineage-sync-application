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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TableauAuthService - specifically testing authentication state management
 * and the "default" site content URL handling.
 */
class TableauAuthServiceTest {

    private MockWebServer mockWebServer;
    private TableauAuthService authService;
    private TableauApiConfig apiConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Setup test configuration
        apiConfig = new TableauApiConfig();
        // Use reflection to set private fields for testing
        TestUtils.setPrivateField(apiConfig, "baseUrl", mockWebServer.url("/").toString().replaceAll("/$", ""));
        TestUtils.setPrivateField(apiConfig, "apiVersion", "3.17");
        TestUtils.setPrivateField(apiConfig, "authMode", "PAT");
        TestUtils.setPrivateField(apiConfig, "patName", "testPat");
        TestUtils.setPrivateField(apiConfig, "patSecret", "testSecret");
        TestUtils.setPrivateField(apiConfig, "defaultSiteId", "");

        // Create WebClient with mock server base URL
        WebClient.Builder webClientBuilder = WebClient.builder();
        
        authService = new TableauAuthService(webClientBuilder, apiConfig);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Sign in should update internal authentication state")
    void testSignInUpdatesInternalState() throws Exception {
        // Prepare mock response
        String mockResponse = """
            {
                "credentials": {
                    "token": "test-auth-token-123",
                    "site": {
                        "id": "site-id-456",
                        "name": "Test Site",
                        "contentUrl": "testsite"
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));

        // Call signIn
        SiteSwitchResponse response = authService.signIn("testsite").block();

        // Verify response
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("test-auth-token-123", response.getAuthToken());
        assertEquals("site-id-456", response.getSiteId());
        assertEquals("Test Site", response.getSiteName());
        assertEquals("testsite", response.getSiteContentUrl());

        // Verify internal state was updated
        assertEquals("site-id-456", authService.getCurrentSiteId());
        assertEquals("Test Site", authService.getCurrentSiteName());
        assertEquals("testsite", authService.getCurrentSiteContentUrl());

        // Verify the request was made correctly
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/3.17/auth/signin", request.getPath());
        
        // Verify request body
        String requestBody = request.getBody().readUtf8();
        assertTrue(requestBody.contains("testsite"));
        assertTrue(requestBody.contains("testPat"));
        assertTrue(requestBody.contains("testSecret"));
    }

    @Test
    @DisplayName("Sign in with 'default' should map to empty string in API call")
    void testSignInWithDefaultMapsToEmptyString() throws Exception {
        // Prepare mock response for default site
        String mockResponse = """
            {
                "credentials": {
                    "token": "test-auth-token-default",
                    "site": {
                        "id": "default-site-id",
                        "name": "Default",
                        "contentUrl": ""
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));

        // Call signIn with "default"
        SiteSwitchResponse response = authService.signIn("default").block();

        // Verify response
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("test-auth-token-default", response.getAuthToken());

        // Verify the request was made with empty contentUrl
        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        
        // Parse the request to verify contentUrl is empty
        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = objectMapper.readValue(requestBody, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = (Map<String, Object>) requestMap.get("credentials");
        @SuppressWarnings("unchecked")
        Map<String, Object> site = (Map<String, Object>) credentials.get("site");
        
        assertEquals("", site.get("contentUrl"), "contentUrl should be empty string for 'default'");
    }

    @Test
    @DisplayName("Sign in with 'DEFAULT' (case-insensitive) should map to empty string")
    void testSignInWithDefaultCaseInsensitive() throws Exception {
        // Prepare mock response
        String mockResponse = """
            {
                "credentials": {
                    "token": "test-token",
                    "site": {
                        "id": "site-id",
                        "name": "Default",
                        "contentUrl": ""
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));

        // Call signIn with "DEFAULT" in uppercase
        authService.signIn("DEFAULT").block();

        // Verify the request
        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = objectMapper.readValue(requestBody, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = (Map<String, Object>) requestMap.get("credentials");
        @SuppressWarnings("unchecked")
        Map<String, Object> site = (Map<String, Object>) credentials.get("site");
        
        assertEquals("", site.get("contentUrl"), "contentUrl should be empty string for 'DEFAULT'");
    }

    @Test
    @DisplayName("Sign in with null should use configured default site")
    void testSignInWithNullUsesConfiguredDefault() throws Exception {
        // Set a default site in config
        TestUtils.setPrivateField(apiConfig, "defaultSiteId", "configuredSite");
        
        // Prepare mock response
        String mockResponse = """
            {
                "credentials": {
                    "token": "test-token",
                    "site": {
                        "id": "site-id",
                        "name": "Configured Site",
                        "contentUrl": "configuredSite"
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"));

        // Call signIn with null
        authService.signIn(null).block();

        // Verify the request uses configured default
        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = objectMapper.readValue(requestBody, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> credentials = (Map<String, Object>) requestMap.get("credentials");
        @SuppressWarnings("unchecked")
        Map<String, Object> site = (Map<String, Object>) credentials.get("site");
        
        assertEquals("configuredSite", site.get("contentUrl"), "Should use configured default site");
    }
}
