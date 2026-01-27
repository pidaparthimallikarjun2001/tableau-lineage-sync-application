package com.example.tableau.service;

import com.example.tableau.config.TableauApiConfig;
import com.example.tableau.test.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TableauGraphQLClient to ensure GraphQL queries work after authentication.
 */
class TableauGraphQLClientIntegrationTest {

    private MockWebServer mockWebServer;
    private TableauAuthService authService;
    private TableauGraphQLClient graphQLClient;
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
        graphQLClient = new TableauGraphQLClient(webClientBuilder, authService, apiConfig);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should be able to execute GraphQL query after signing in")
    void testExecuteQueryAfterSignIn() throws Exception {
        // Mock sign-in response
        String signInResponse = """
            {
                "credentials": {
                    "token": "test-graphql-token",
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

        // Mock GraphQL response
        String graphQLResponse = """
            {
                "data": {
                    "workbooksConnection": {
                        "nodes": [
                            {
                                "id": "workbook-1",
                                "name": "Test Workbook",
                                "luid": "wb-luid-123"
                            }
                        ],
                        "pageInfo": {
                            "hasNextPage": false,
                            "endCursor": null
                        }
                    }
                }
            }
            """;
        
        mockWebServer.enqueue(new MockResponse()
                .setBody(graphQLResponse)
                .addHeader("Content-Type", "application/json"));

        // Execute a GraphQL query
        String query = "query { workbooksConnection { nodes { id name } } }";
        Map<String, Object> variables = new HashMap<>();
        
        JsonNode result = graphQLClient.executeQuery(query, variables).block();
        
        // Verify the result
        assertNotNull(result);
        JsonNode workbooks = result.path("data").path("workbooksConnection").path("nodes");
        assertTrue(workbooks.isArray());
        assertEquals(1, workbooks.size());
        assertEquals("Test Workbook", workbooks.get(0).path("name").asText());

        // Verify auth token was used
        mockWebServer.takeRequest(); // skip sign-in request
        RecordedRequest graphQLRequest = mockWebServer.takeRequest();
        assertEquals("/api/metadata/graphql", graphQLRequest.getPath());
        assertEquals("test-graphql-token", graphQLRequest.getHeader("X-Tableau-Auth"));
    }

    @Test
    @DisplayName("Should be able to fetch workbooks after signing in")
    void testFetchWorkbooksAfterSignIn() throws Exception {
        // Mock sign-in response
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "credentials": {
                            "token": "test-token",
                            "site": {
                                "id": "site-id",
                                "name": "Test Site",
                                "contentUrl": ""
                            }
                        }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        // Sign in to default site
        authService.signIn("default").block();

        // Mock GraphQL workbooks response
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "data": {
                            "workbooksConnection": {
                                "nodes": [
                                    {
                                        "id": "wb1",
                                        "name": "Workbook 1",
                                        "luid": "luid1"
                                    },
                                    {
                                        "id": "wb2",
                                        "name": "Workbook 2",
                                        "luid": "luid2"
                                    }
                                ],
                                "pageInfo": {
                                    "hasNextPage": false,
                                    "endCursor": null
                                }
                            }
                        }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        // Fetch workbooks
        List<JsonNode> workbooks = graphQLClient.fetchWorkbooks(100).block();
        
        // Verify results
        assertNotNull(workbooks);
        assertEquals(2, workbooks.size());
        assertEquals("Workbook 1", workbooks.get(0).path("name").asText());
        assertEquals("Workbook 2", workbooks.get(1).path("name").asText());

        // Verify auth token was used
        mockWebServer.takeRequest(); // skip sign-in
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("test-token", request.getHeader("X-Tableau-Auth"));
    }

    @Test
    @DisplayName("Should be able to fetch projects after signing in")
    void testFetchProjectsAfterSignIn() throws Exception {
        // Mock sign-in
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "credentials": {
                            "token": "project-token",
                            "site": {
                                "id": "site-id",
                                "name": "Test Site",
                                "contentUrl": "mysite"
                            }
                        }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        authService.signIn("mysite").block();

        // Mock projects response
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "data": {
                            "projectsConnection": {
                                "nodes": [
                                    {
                                        "id": "proj1",
                                        "name": "Default",
                                        "luid": "proj-luid-1"
                                    }
                                ],
                                "pageInfo": {
                                    "hasNextPage": false,
                                    "endCursor": null
                                }
                            }
                        }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        // Fetch projects
        List<JsonNode> projects = graphQLClient.fetchProjects(100).block();
        
        // Verify
        assertNotNull(projects);
        assertEquals(1, projects.size());
        assertEquals("Default", projects.get(0).path("name").asText());
    }

    @Test
    @DisplayName("GraphQL queries should use authentication token from sign-in")
    void testGraphQLUsesAuthToken() throws Exception {
        String expectedToken = "my-special-token-12345";
        
        // Mock sign-in
        mockWebServer.enqueue(new MockResponse()
                .setBody(String.format("""
                    {
                        "credentials": {
                            "token": "%s",
                            "site": {
                                "id": "site-id",
                                "name": "Test Site",
                                "contentUrl": "testsite"
                            }
                        }
                    }
                    """, expectedToken))
                .addHeader("Content-Type", "application/json"));

        authService.signIn("testsite").block();

        // Mock GraphQL response
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "data": {
                            "workbooksConnection": {
                                "nodes": [],
                                "pageInfo": {
                                    "hasNextPage": false,
                                    "endCursor": null
                                }
                            }
                        }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        // Execute query
        graphQLClient.fetchWorkbooks(10).block();

        // Verify the GraphQL request used the correct token
        mockWebServer.takeRequest(); // skip sign-in
        RecordedRequest graphQLRequest = mockWebServer.takeRequest();
        assertEquals(expectedToken, graphQLRequest.getHeader("X-Tableau-Auth"),
                "GraphQL request should use the auth token from sign-in");
    }
}
