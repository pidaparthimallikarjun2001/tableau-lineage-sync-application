package com.example.tableau.service;

import com.example.tableau.config.CollibraApiConfig;
import com.example.tableau.dto.collibra.CollibraAsset;
import com.example.tableau.dto.collibra.CollibraAttributeValue;
import com.example.tableau.dto.collibra.CollibraCommunity;
import com.example.tableau.dto.collibra.CollibraDomain;
import com.example.tableau.dto.collibra.CollibraIdentifier;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.dto.collibra.CollibraType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CollibraRestClient batch processing functionality.
 */
class CollibraRestClientBatchTest {

    private MockWebServer mockWebServer;
    private CollibraRestClient collibraClient;
    private CollibraApiConfig config;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        config = new CollibraApiConfig();
        // Use reflection to set the private fields for testing
        setField(config, "baseUrl", mockWebServer.url("/").toString().replaceAll("/$", ""));
        setField(config, "username", "testuser");
        setField(config, "password", "testpass");
        setField(config, "connectionTimeout", 5000);
        setField(config, "readTimeout", 10000);
        setField(config, "batchSize", 10); // Small batch size for testing

        objectMapper = new ObjectMapper();
        collibraClient = new CollibraRestClient(config, objectMapper);
        collibraClient.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testImportAssets_SmallList_SingleBatch() {
        // Create a small list of assets (less than batch size)
        List<CollibraAsset> assets = createTestAssets(5);

        // Mock the server response
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-123\"}"));

        // Execute the import
        CollibraIngestionResult result = collibraClient.importAssets(assets, "TestAsset").block();

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(5, result.getTotalProcessed());
        assertEquals("job-123", result.getJobId());

        // Verify only one request was made
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void testImportAssets_LargeList_MultipleBatches() throws InterruptedException {
        // Create a large list of assets (more than batch size)
        List<CollibraAsset> assets = createTestAssets(25); // Will be split into 3 batches (10, 10, 5)

        // Mock the server responses for each batch
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-1\"}"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-2\"}"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-3\"}"));

        // Execute the import
        CollibraIngestionResult result = collibraClient.importAssets(assets, "TestAsset").block();

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(25, result.getTotalProcessed());
        assertEquals("job-3", result.getJobId()); // Last job ID

        // Verify three requests were made (one per batch)
        assertEquals(3, mockWebServer.getRequestCount());

        // Verify each request had the correct batch size
        RecordedRequest request1 = mockWebServer.takeRequest();
        RecordedRequest request2 = mockWebServer.takeRequest();
        RecordedRequest request3 = mockWebServer.takeRequest();

        assertNotNull(request1);
        assertNotNull(request2);
        assertNotNull(request3);
    }

    @Test
    void testImportAssets_ExactlyBatchSize() {
        // Create exactly one batch worth of assets
        List<CollibraAsset> assets = createTestAssets(10);

        // Mock the server response
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-123\"}"));

        // Execute the import
        CollibraIngestionResult result = collibraClient.importAssets(assets, "TestAsset").block();

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(10, result.getTotalProcessed());

        // Verify only one request was made
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void testImportAssets_EmptyList() {
        // Test with empty list
        List<CollibraAsset> assets = new ArrayList<>();

        // Execute the import
        CollibraIngestionResult result = collibraClient.importAssets(assets, "TestAsset").block();

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalProcessed());

        // Verify no requests were made
        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    void testImportAssets_CustomBatchSize() {
        // Create assets with custom batch size
        List<CollibraAsset> assets = createTestAssets(15);

        // Mock the server responses for custom batch size of 7
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-1\"}"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-2\"}"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-3\"}"));

        // Execute the import with custom batch size (7: will create 3 batches: 7, 7, 1)
        CollibraIngestionResult result = collibraClient.importAssets(assets, "TestAsset", 7).block();

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(15, result.getTotalProcessed());

        // Verify three requests were made
        assertEquals(3, mockWebServer.getRequestCount());
    }

    @Test
    void testImportAssets_BatchFailure() {
        // Create assets for multiple batches
        List<CollibraAsset> assets = createTestAssets(15);

        // Mock success for first batch, failure for second
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"job-1\"}"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"error\":\"Internal server error\"}"));

        // Execute the import
        CollibraIngestionResult result = collibraClient.importAssets(assets, "TestAsset").block();

        // Verify the result indicates failure
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("500") || result.getMessage().contains("error"));
    }

    /**
     * Helper method to create test assets.
     */
    private List<CollibraAsset> createTestAssets(int count) {
        List<CollibraAsset> assets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CollibraAsset asset = CollibraAsset.builder()
                    .resourceType("Asset")
                    .type(CollibraType.builder().name("TestAssetType").build())
                    .displayName("Test Asset " + i)
                    .identifier(CollibraIdentifier.builder()
                            .name("TestAsset" + i)
                            .domain(CollibraDomain.builder()
                                    .name("Test Domain")
                                    .community(CollibraCommunity.builder()
                                            .name("Test Community")
                                            .build())
                                    .build())
                            .build())
                    .build();
            assets.add(asset);
        }
        return assets;
    }

    /**
     * Helper method to set private fields using reflection.
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
