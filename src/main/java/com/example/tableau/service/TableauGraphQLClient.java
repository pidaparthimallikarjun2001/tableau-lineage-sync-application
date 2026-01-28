package com.example.tableau.service;

import com.example.tableau.config.TableauApiConfig;
import com.example.tableau.exception.TableauApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Service for making GraphQL requests to Tableau's Metadata API.
 * 
 * Note: Tableau's GraphQL API (Metadata API) operates at the site level,
 * meaning queries will retrieve data within the context of the currently active site.
 */
@Service
public class TableauGraphQLClient {
    private static final Logger log = LoggerFactory.getLogger(TableauGraphQLClient.class);

    private final WebClient webClient;
    private final TableauAuthService authService;
    private final TableauApiConfig apiConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    public TableauGraphQLClient(WebClient.Builder webClientBuilder, 
                                TableauAuthService authService,
                                TableauApiConfig apiConfig) {
        this.webClient = webClientBuilder.baseUrl(apiConfig.getBaseUrl()).build();
        this.authService = authService;
        this.apiConfig = apiConfig;
    }

    /**
     * Execute a GraphQL query and return the result as JsonNode.
     */
    public Mono<JsonNode> executeQuery(String query, Map<String, Object> variables) {
        return authService.getAuthToken()
                .flatMap(token -> {
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("query", query);
                    if (variables != null && !variables.isEmpty()) {
                        requestBody.put("variables", variables);
                    }
                    
                    try {
                        String body = mapper.writeValueAsString(requestBody);
                        log.debug("Executing GraphQL query: {}", query.substring(0, Math.min(200, query.length())));
                        
                        return webClient.post()
                                .uri("/api/metadata/graphql")
                                .header("X-Tableau-Auth", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(this::parseGraphQLResponse)
                                .retryWhen(Retry.backoff(5, Duration.ofSeconds(2))
                                        .maxBackoff(Duration.ofSeconds(10))
                                        .filter(this::isRetryableError)
                                        .doBeforeRetry(signal -> 
                                            log.warn("Retrying GraphQL request due to {}, attempt {}", 
                                                signal.failure().getClass().getSimpleName(), 
                                                signal.totalRetries() + 1)));
                    } catch (Exception e) {
                        return Mono.error(new TableauApiException("Failed to execute GraphQL query", e));
                    }
                });
    }

    /**
     * Execute a GraphQL query with pagination support.
     */
    public Mono<List<JsonNode>> executeQueryWithPagination(String query, String dataPath, int pageSize) {
        List<JsonNode> allResults = new ArrayList<>();
        return executePagedQuery(query, dataPath, pageSize, 0, null, allResults);
    }

    private Mono<List<JsonNode>> executePagedQuery(String query, String dataPath, 
                                                    int pageSize, int offset, String cursor,
                                                    List<JsonNode> results) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("first", pageSize);
        if (cursor != null) {
            variables.put("after", cursor);
        }
        
        return executeQuery(query, variables)
                .flatMap(response -> {
                    // Navigate to the connection object
                    JsonNode connection = navigateToPath(response, dataPath);
                    if (connection == null) {
                        return Mono.just(results);
                    }
                    
                    // Get nodes array
                    JsonNode nodes = connection.path("nodes");
                    if (nodes != null && nodes.isArray()) {
                        nodes.forEach(results::add);
                    }
                    
                    // Check for pagination info
                    JsonNode pageInfo = connection.path("pageInfo");
                    boolean hasNextPage = pageInfo.path("hasNextPage").asBoolean(false);
                    String endCursor = pageInfo.path("endCursor").asText(null);
                    
                    if (hasNextPage && endCursor != null) {
                        return executePagedQuery(query, dataPath, pageSize, offset, endCursor, results);
                    }
                    
                    return Mono.just(results);
                });
    }

    /**
     * Query for sites (from REST API, as GraphQL is site-scoped).
     */
    public static final String SITES_QUERY = """
        query getSites {
            tableauSites {
                id
                name
                uri
                luid
            }
        }
        """;

    /**
     * Query for projects in the current site.
     */
    public static final String PROJECTS_QUERY = """
        query getProjects($first: Int!, $after: String) {
            projectsConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    luid
                    description
                    vizportalUrlId
                    parentProject {
                        id
                        name
                        luid
                    }
                    owner {
                        id
                        name
                        username
                    }
                }
            }
        }
        """;

    /**
     * Query for workbooks in the current site.
     */
    public static final String WORKBOOKS_QUERY = """
        query getWorkbooks($first: Int!, $after: String) {
            workbooksConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    luid
                    description
                    createdAt
                    updatedAt
                    uri
                    vizportalUrlId
                    projectName
                    projectVizportalUrlId
                    owner {
                        id
                        name
                        username
                    }
                    sheets {
                        id
                        name
                        luid
                    }
                    embeddedDatasources {
                        id
                        name
                    }
                    upstreamDatasources {
                        id
                        name
                        luid
                    }
                }
            }
        }
        """;

    /**
     * Query for worksheets (sheets) with field information.
     */
    public static final String WORKSHEETS_QUERY = """
        query getSheets($first: Int!, $after: String) {
            sheetsConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    luid
                    workbook {
                        id
                        name
                        luid
                    }
                    sheetFieldInstances {
                        id
                        name
                        datasource {
                            id
                            name
                        }
                    }
                    upstreamFields {
                        id
                        name
                        __typename
                        datasource {
                            id
                            name
                        }
                        ... on ColumnField {
                            dataType
                        }
                        ... on CalculatedField {
                            dataType
                            formula
                        }
                    }
                    upstreamDatasources {
                        id
                        name
                    }
                }
            }
        }
        """;

    /**
     * Query for detailed sheet field instances (report attributes) with lineage.
     * Note: sheetFieldInstances is a nested field within Sheet, not a top-level query.
     * We query sheets and extract their field instances.
     */
    public static final String SHEET_FIELDS_QUERY = """
        query getSheetFieldInstances($first: Int!, $after: String) {
            sheetsConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    luid
                    workbook {
                        id
                        name
                        luid
                    }
                    sheetFieldInstances {
                        id
                        name
                        role
                        datasource {
                            id
                            name
                        }
                    }
                    upstreamFields {
                        id
                        name
                        __typename
                        datasource {
                            id
                            name
                        }
                        upstreamTables {
                            id
                            name
                            fullName
                            database {
                                id
                                name
                                connectionType
                            }
                        }
                        upstreamColumns {
                            id
                            name
                            remoteType
                            table {
                                id
                                name
                            }
                        }
                        ... on ColumnField {
                            dataType
                        }
                        ... on CalculatedField {
                            dataType
                            formula
                        }
                    }
                    upstreamDatasources {
                        id
                        name
                    }
                }
            }
        }
        """;

    /**
     * Query for calculated fields with formulas.
     */
    public static final String CALCULATED_FIELDS_QUERY = """
        query getCalculatedFields($first: Int!, $after: String) {
            calculatedFieldsConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    formula
                    dataType
                    role
                    datasource {
                        id
                        name
                        luid
                    }
                    fields {
                        id
                        name
                        dataType
                    }
                    referencedByCalculations {
                        id
                        name
                        formula
                    }
                }
            }
        }
        """;

    /**
     * Query for data sources with connection details.
     */
    public static final String DATASOURCES_QUERY = """
        query getDatasources($first: Int!, $after: String) {
            publishedDatasourcesConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    luid
                    description
                    isCertified
                    certificationNote
                    hasExtracts
                    extractLastRefreshTime
                    extractLastUpdateTime
                    owner {
                        id
                        name
                        username
                    }
                    projectName
                    projectVizportalUrlId
                    upstreamTables {
                        id
                        name
                        fullName
                        schema
                        connectionType
                        database {
                            id
                            name
                            connectionType
                        }
                    }
                    fields {
                        id
                        name
                        __typename
                        upstreamTables {
                            id
                            name
                            fullName
                        }
                        upstreamColumns {
                            id
                            name
                            remoteType
                        }
                        ... on ColumnField {
                            dataType
                        }
                        ... on CalculatedField {
                            dataType
                            formula
                        }
                    }
                }
            }
        }
        """;

    /**
     * Query for embedded data sources in workbooks.
     */
    public static final String EMBEDDED_DATASOURCES_QUERY = """
        query getEmbeddedDatasources($first: Int!, $after: String) {
            embeddedDatasourcesConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    hasExtracts
                    workbook {
                        id
                        name
                        luid
                    }
                    upstreamTables {
                        id
                        name
                        fullName
                        schema
                        connectionType
                        isEmbedded
                        database {
                            id
                            name
                            connectionType
                        }
                    }
                    upstreamDatasources {
                        id
                        name
                        luid
                    }
                    fields {
                        id
                        name
                        __typename
                        ... on ColumnField {
                            dataType
                        }
                        ... on CalculatedField {
                            dataType
                            formula
                        }
                    }
                }
            }
        }
        """;

    /**
     * Query for custom SQL tables.
     */
    public static final String CUSTOM_SQL_TABLES_QUERY = """
        query getCustomSQLTables($first: Int!, $after: String) {
            customSQLTablesConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    query
                    isEmbedded
                    connectionType
                    database {
                        id
                        name
                        connectionType
                    }
                    columns {
                        id
                        name
                        remoteType
                    }
                    downstreamDatasources {
                        id
                        name
                        luid
                    }
                }
            }
        }
        """;

    /**
     * Query for database tables.
     */
    public static final String DATABASE_TABLES_QUERY = """
        query getDatabaseTables($first: Int!, $after: String) {
            databaseTablesConnection(first: $first, after: $after) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                nodes {
                    id
                    name
                    fullName
                    schema
                    connectionType
                    isEmbedded
                    database {
                        id
                        name
                        connectionType
                    }
                    columns {
                        id
                        name
                        remoteType
                    }
                }
            }
        }
        """;

    /**
     * Fetch projects from the current site.
     */
    public Mono<List<JsonNode>> fetchProjects(int pageSize) {
        return executeQueryWithPagination(PROJECTS_QUERY, "data.projectsConnection", pageSize);
    }

    /**
     * Fetch workbooks from the current site.
     */
    public Mono<List<JsonNode>> fetchWorkbooks(int pageSize) {
        return executeQueryWithPagination(WORKBOOKS_QUERY, "data.workbooksConnection", pageSize);
    }

    /**
     * Fetch worksheets/sheets from the current site.
     */
    public Mono<List<JsonNode>> fetchWorksheets(int pageSize) {
        return executeQueryWithPagination(WORKSHEETS_QUERY, "data.sheetsConnection", pageSize);
    }

    /**
     * Fetch sheet field instances from the current site.
     * Since sheetFieldInstances are nested within sheets, we query sheets
     * and flatten the field instances, attaching sheet/workbook context to each.
     */
    public Mono<List<JsonNode>> fetchSheetFieldInstances(int pageSize) {
        return executeQueryWithPagination(SHEET_FIELDS_QUERY, "data.sheetsConnection", pageSize)
                .map(sheets -> {
                    List<JsonNode> flattenedFieldInstances = new ArrayList<>();
                    for (JsonNode sheet : sheets) {
                        JsonNode sheetFieldInstances = sheet.path("sheetFieldInstances");
                        JsonNode upstreamFields = sheet.path("upstreamFields");
                        
                        if (sheetFieldInstances.isArray()) {
                            for (JsonNode fieldInstance : sheetFieldInstances) {
                                // Create an enhanced object that includes sheet context
                                Map<String, Object> enhancedInstance = new HashMap<>();
                                
                                // Copy field instance properties
                                enhancedInstance.put("id", fieldInstance.path("id").asText());
                                enhancedInstance.put("name", fieldInstance.path("name").asText());
                                
                                // Add role field - always include even if null to ensure field is present
                                String role = fieldInstance.path("role").asText(null);
                                enhancedInstance.put("role", role);
                                if (role == null || role.isEmpty()) {
                                    log.debug("Field instance {} has null or empty role", fieldInstance.path("id").asText());
                                }
                                
                                // Add datasource info from field instance
                                JsonNode datasource = fieldInstance.path("datasource");
                                if (!datasource.isMissingNode()) {
                                    enhancedInstance.put("datasource", mapper.convertValue(datasource, Map.class));
                                }
                                
                                // Add sheet context (use id only for worksheets)
                                Map<String, Object> sheetContext = new HashMap<>();
                                sheetContext.put("id", sheet.path("id").asText());
                                sheetContext.put("name", sheet.path("name").asText());
                                // Note: luid not used for worksheets as it's frequently null
                                
                                // Add workbook context
                                JsonNode workbook = sheet.path("workbook");
                                if (!workbook.isMissingNode()) {
                                    Map<String, Object> workbookContext = new HashMap<>();
                                    workbookContext.put("id", workbook.path("id").asText());
                                    workbookContext.put("name", workbook.path("name").asText());
                                    workbookContext.put("luid", workbook.path("luid").asText());
                                    sheetContext.put("workbook", workbookContext);
                                }
                                enhancedInstance.put("sheet", sheetContext);
                                
                                // Add upstream fields from the sheet level
                                if (upstreamFields.isArray() && !upstreamFields.isEmpty()) {
                                    enhancedInstance.put("upstreamFields", mapper.convertValue(upstreamFields, List.class));
                                }
                                
                                try {
                                    flattenedFieldInstances.add(mapper.valueToTree(enhancedInstance));
                                } catch (Exception e) {
                                    log.warn("Failed to convert field instance to JsonNode: id={}, name={}, error={}", 
                                            fieldInstance.path("id").asText(), 
                                            fieldInstance.path("name").asText(),
                                            e.getMessage());
                                }
                            }
                        }
                    }
                    return flattenedFieldInstances;
                });
    }

    /**
     * Fetch published data sources from the current site.
     */
    public Mono<List<JsonNode>> fetchPublishedDatasources(int pageSize) {
        return executeQueryWithPagination(DATASOURCES_QUERY, "data.publishedDatasourcesConnection", pageSize);
    }

    /**
     * Fetch embedded data sources from the current site.
     */
    public Mono<List<JsonNode>> fetchEmbeddedDatasources(int pageSize) {
        return executeQueryWithPagination(EMBEDDED_DATASOURCES_QUERY, "data.embeddedDatasourcesConnection", pageSize);
    }

    /**
     * Fetch custom SQL tables from the current site.
     */
    public Mono<List<JsonNode>> fetchCustomSQLTables(int pageSize) {
        return executeQueryWithPagination(CUSTOM_SQL_TABLES_QUERY, "data.customSQLTablesConnection", pageSize);
    }

    private JsonNode parseGraphQLResponse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            
            // Check for GraphQL errors
            JsonNode errors = root.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder("GraphQL errors: ");
                errors.forEach(error -> errorMsg.append(error.path("message").asText()).append("; "));
                log.error(errorMsg.toString());
                throw new TableauApiException(errorMsg.toString());
            }
            
            return root;
        } catch (TableauApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse GraphQL response: {}", e.getMessage());
            throw new TableauApiException("Failed to parse GraphQL response", e);
        }
    }

    private JsonNode navigateToPath(JsonNode root, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            current = current.path(part);
            if (current.isMissingNode()) {
                return null;
            }
        }
        return current;
    }

    private boolean isRetryableError(Throwable throwable) {
        // Retry on HTTP 429 (Too Many Requests) or 5xx server errors
        if (throwable instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        
        // Retry on PrematureCloseException - connection closed before response
        if (throwable instanceof PrematureCloseException) {
            log.warn("Connection prematurely closed, will retry");
            return true;
        }
        
        // Retry on IOException (includes connection reset, broken pipe, etc.)
        if (throwable instanceof IOException) {
            log.warn("IO Exception occurred: {}, will retry", throwable.getMessage());
            return true;
        }
        
        // Traverse the full exception chain to find retryable causes
        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (cause instanceof PrematureCloseException || cause instanceof IOException) {
                log.warn("Connection issue in cause chain: {}, will retry", cause.getClass().getSimpleName());
                return true;
            }
            cause = cause.getCause();
        }
        
        return false;
    }
}
