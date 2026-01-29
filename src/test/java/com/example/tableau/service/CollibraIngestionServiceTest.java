package com.example.tableau.service;

import com.example.tableau.config.CollibraApiConfig;
import com.example.tableau.dto.collibra.CollibraAsset;
import com.example.tableau.dto.collibra.CollibraAttributeValue;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
import com.example.tableau.dto.collibra.CollibraRelationTarget;
import com.example.tableau.entity.*;
import com.example.tableau.enums.StatusFlag;
import com.example.tableau.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CollibraIngestionService.
 */
@ExtendWith(MockitoExtension.class)
class CollibraIngestionServiceTest {

    @Mock
    private CollibraRestClient collibraClient;

    @Mock
    private CollibraApiConfig collibraConfig;

    @Mock
    private TableauServerRepository serverRepository;

    @Mock
    private TableauSiteRepository siteRepository;

    @Mock
    private TableauProjectRepository projectRepository;

    @Mock
    private TableauWorkbookRepository workbookRepository;

    @Mock
    private TableauWorksheetRepository worksheetRepository;

    @Mock
    private TableauDataSourceRepository dataSourceRepository;

    @Mock
    private ReportAttributeRepository reportAttributeRepository;

    private CollibraIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new CollibraIngestionService(
                collibraClient,
                collibraConfig,
                serverRepository,
                siteRepository,
                projectRepository,
                workbookRepository,
                worksheetRepository,
                dataSourceRepository,
                reportAttributeRepository
        );
    }

    @Test
    void testIngestServersToCollibra_NotConfigured() {
        when(collibraClient.isConfigured()).thenReturn(false);

        CollibraIngestionResult result = ingestionService.ingestServersToCollibra().block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not configured"));
    }

    @Test
    void testIngestServersToCollibra_EmptyList() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(Collections.emptyList());
        when(collibraClient.importAssets(anyList(), eq("Server")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Server", 0, 0, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestServersToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalProcessed());
    }

    @Test
    void testIngestServersToCollibra_WithNewServer() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getServerDomainName()).thenReturn("Tableau Server");

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.NEW);
        when(serverRepository.findAll()).thenReturn(List.of(server));

        when(collibraClient.importAssets(anyList(), eq("Server")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Server", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestServersToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        verify(collibraClient).importAssets(anyList(), eq("Server"));
    }

    @Test
    void testIngestServersToCollibra_WithActiveServer_Skipped() {
        when(collibraClient.isConfigured()).thenReturn(true);

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.ACTIVE);
        when(serverRepository.findAll()).thenReturn(List.of(server));

        when(collibraClient.importAssets(eq(Collections.emptyList()), eq("Server")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Server", 0, 0, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestServersToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAssetsSkipped());
    }

    @Test
    void testCreateFullName() {
        String fullName = CollibraAsset.createFullName("site-123", "asset-456", "My Asset");
        assertEquals("site-123>asset-456>My Asset", fullName);
    }

    @Test
    void testCreateFullName_WithNullValues() {
        String fullName = CollibraAsset.createFullName(null, null, null);
        assertEquals("default>unknown>unnamed", fullName);
    }

    @Test
    void testCreateServerFullName() {
        String fullName = CollibraAsset.createServerFullName("server-123", "Test Server");
        assertEquals("server>server-123>Test Server", fullName);
    }

    @Test
    void testIngestSingleServerToCollibra() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getServerDomainName()).thenReturn("Tableau Server");

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.NEW);
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));

        when(collibraClient.importAssets(anyList(), eq("Server")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Server", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestServerToCollibra(1L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIngestSingleServerToCollibra_NotFound() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());

        CollibraIngestionResult result = ingestionService.ingestServerToCollibra(999L).block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not found"));
    }

    @Test
    void testIngestProjectsWithParentRelation() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauProject parentProject = createTestProject("parent-proj", "Parent Project", "site-1", null, StatusFlag.ACTIVE);
        TableauProject childProject = createTestProject("child-proj", "Child Project", "site-1", "parent-proj", StatusFlag.NEW);

        // Batch ingestion no longer needs this stub since it uses the projectMap internally
        when(projectRepository.findAllWithSiteAndServer()).thenReturn(List.of(parentProject, childProject));

        when(collibraClient.importAssets(anyList(), eq("Project")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Project", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestProjectsToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIsConfigured() {
        when(collibraClient.isConfigured()).thenReturn(true);
        assertTrue(ingestionService.isConfigured());

        when(collibraClient.isConfigured()).thenReturn(false);
        assertFalse(ingestionService.isConfigured());
    }

    @Test
    void testIngestProjectWithUrlGeneration() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.ACTIVE);
        TableauSite site = createTestSite("site-1", "Test Site", "testsite", server, StatusFlag.ACTIVE);
        TableauProject project = createTestProjectWithSite("proj-1", "Test Project", "site-1", null, site, StatusFlag.NEW);

        when(projectRepository.findAllWithSiteAndServer()).thenReturn(List.of(project));
        when(collibraClient.importAssets(anyList(), eq("Project")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Project", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestProjectsToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testProjectResourceTypeIsAsset() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.ACTIVE);
        TableauSite site = createTestSite("site-1", "Test Site", "testsite", server, StatusFlag.ACTIVE);
        TableauProject project = createTestProjectWithSite("proj-1", "Test Project", "site-1", null, site, StatusFlag.NEW);

        when(projectRepository.findAllWithSiteAndServer()).thenReturn(List.of(project));
        when(collibraClient.importAssets(anyList(), eq("Project")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    assertNotNull(assets);
                    assertFalse(assets.isEmpty());
                    
                    // Verify that the project's resourceType is "Asset"
                    CollibraAsset projectAsset = assets.get(0);
                    assertEquals("Asset", projectAsset.getResourceType(), 
                        "Project resourceType should be 'Asset'");
                    assertEquals("Tableau Project", projectAsset.getType().getName(),
                        "Project type name should be 'Tableau Project'");
                    
                    return Mono.just(CollibraIngestionResult.success("Project", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestProjectsToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(collibraClient).importAssets(anyList(), eq("Project"));
    }

    private TableauServer createTestServer(String assetId, String name, StatusFlag statusFlag) {
        return TableauServer.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .serverUrl("https://tableau.example.com")
                .version("2023.3")
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    private TableauSite createTestSite(String assetId, String name, String contentUrl, 
                                       TableauServer server, StatusFlag statusFlag) {
        return TableauSite.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .contentUrl(contentUrl)
                .siteUrl(server.getServerUrl() + "/#/site/" + contentUrl + "/")
                .server(server)
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    private TableauProject createTestProject(String assetId, String name, String siteId, 
                                              String parentProjectId, StatusFlag statusFlag) {
        return TableauProject.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .siteId(siteId)
                .parentProjectId(parentProjectId)
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    private TableauProject createTestProjectWithSite(String assetId, String name, String siteId,
                                                      String parentProjectId, TableauSite site, StatusFlag statusFlag) {
        return TableauProject.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .siteId(siteId)
                .parentProjectId(parentProjectId)
                .site(site)
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    private TableauWorkbook createTestWorkbook(String assetId, String name, String siteId, 
                                                TableauProject project, StatusFlag statusFlag) {
        return TableauWorkbook.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .siteId(siteId)
                .description("Test workbook description")
                .owner("test.user@example.com")
                .tableauCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .tableauUpdatedAt(LocalDateTime.of(2024, 2, 20, 14, 45))
                .project(project)
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    private TableauWorksheet createTestWorksheet(String assetId, String name, String siteId,
                                                  TableauWorkbook workbook, StatusFlag statusFlag) {
        return TableauWorksheet.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .siteId(siteId)
                .owner("test.user@example.com")
                .workbook(workbook)
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void testWorkbookIdentifierNameFormat() {
        // Test that workbook identifier name follows format: siteid > workbookid
        String identifierName = CollibraAsset.createWorkbookIdentifierName("site-123", "workbook-456", "My Workbook");
        assertEquals("site-123 > workbook-456", identifierName);
    }

    @Test
    void testWorksheetIdentifierNameFormat() {
        // Test that worksheet identifier name follows format: siteid > worksheetid
        String identifierName = CollibraAsset.createWorksheetIdentifierName("site-123", "worksheet-789", "My Worksheet");
        assertEquals("site-123 > worksheet-789", identifierName);
    }

    @Test
    void testSiteIdentifierNameFormat() {
        // Test that site identifier name follows format: siteid
        String identifierName = CollibraAsset.createSiteIdentifierName("site-123", "My Site");
        assertEquals("site-123", identifierName);
    }

    @Test
    void testServerIdentifierNameFormat() {
        // Test that server identifier name follows format: serverid
        String identifierName = CollibraAsset.createServerIdentifierName("server-456", "My Server");
        assertEquals("server-456", identifierName);
    }

    @Test
    void testProjectIdentifierNameFormat() {
        // Test that project identifier name follows format: siteid > projectid
        String identifierName = CollibraAsset.createProjectIdentifierName("site-123", "project-789", "My Project");
        assertEquals("site-123 > project-789", identifierName);
    }

    @Test
    void testIngestWorkbooksWithNewAttributes() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        TableauWorkbook workbook = createTestWorkbook("workbook-1", "Test Workbook", "site-1", project, StatusFlag.NEW);

        when(workbookRepository.findAllWithProject()).thenReturn(List.of(workbook));
        when(collibraClient.importAssets(anyList(), eq("Workbook")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    assertNotNull(assets);
                    assertEquals(1, assets.size());
                    
                    CollibraAsset asset = assets.get(0);
                    // Verify identifier format: siteid > workbookid
                    assertEquals("site-1 > workbook-1", asset.getIdentifier().getName());
                    
                    // Verify attributes
                    assertNotNull(asset.getAttributes());
                    assertTrue(asset.getAttributes().containsKey("Description"));
                    assertTrue(asset.getAttributes().containsKey("Owner in Source"));
                    assertTrue(asset.getAttributes().containsKey("Document creation date"));
                    assertTrue(asset.getAttributes().containsKey("Document modification date"));
                    
                    // Verify old attributes are removed
                    assertFalse(asset.getAttributes().containsKey("Content URL"));
                    assertFalse(asset.getAttributes().containsKey("Site ID"));
                    assertFalse(asset.getAttributes().containsKey("Owner")); // Changed to "Owner in Source"
                    
                    // Verify relation to project with correct relation ID
                    assertNotNull(asset.getRelations());
                    assertTrue(asset.getRelations().containsKey("0195fcea-cc73-7284-88a6-ea770982b1ba:SOURCE"));
                    
                    return Mono.just(CollibraIngestionResult.success("Workbook", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestWorkbooksToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(collibraClient).importAssets(anyList(), eq("Workbook"));
        verify(workbookRepository).findAllWithProject();
    }

    @Test
    void testWorkbookDateFormatting() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        TableauWorkbook workbook = createTestWorkbook("workbook-1", "Test Workbook", "site-1", project, StatusFlag.NEW);

        when(workbookRepository.findAllWithProject()).thenReturn(List.of(workbook));
        when(collibraClient.importAssets(anyList(), eq("Workbook")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    
                    // Verify date format is Unix timestamp in milliseconds
                    // LocalDateTime(2024, 1, 15, 10, 30) -> LocalDate(2024-01-15) at UTC midnight
                    // LocalDateTime(2024, 2, 20, 14, 45) -> LocalDate(2024-02-20) at UTC midnight
                    List<CollibraAttributeValue> creationDates = asset.getAttributes().get("Document creation date");
                    assertNotNull(creationDates, "Document creation date should be present");
                    assertEquals(1, creationDates.size());
                    // 2024-01-15T00:00:00Z = 1705276800000
                    assertEquals("1705276800000", creationDates.get(0).getValue(), 
                        "Creation date should be Unix timestamp in milliseconds for 2024-01-15 at UTC midnight");
                    
                    List<CollibraAttributeValue> modificationDates = asset.getAttributes().get("Document modification date");
                    assertNotNull(modificationDates, "Document modification date should be present");
                    assertEquals(1, modificationDates.size());
                    // 2024-02-20T00:00:00Z = 1708387200000
                    assertEquals("1708387200000", modificationDates.get(0).getValue(), 
                        "Modification date should be Unix timestamp in milliseconds for 2024-02-20 at UTC midnight");
                    
                    return Mono.just(CollibraIngestionResult.success("Workbook", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestWorkbooksToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(collibraClient).importAssets(anyList(), eq("Workbook"));
    }

    @Test
    void testIngestWorksheetsWithNoAttributes() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorksheetDomainName()).thenReturn("Tableau Worksheets");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");

        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        TableauWorkbook workbook = createTestWorkbook("workbook-1", "Test Workbook", "site-1", project, StatusFlag.ACTIVE);
        TableauWorksheet worksheet = createTestWorksheet("worksheet-1", "Test Worksheet", "site-1", workbook, StatusFlag.NEW);

        when(worksheetRepository.findAllWithWorkbook()).thenReturn(List.of(worksheet));
        when(collibraClient.importAssets(anyList(), eq("Worksheet")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    assertNotNull(assets);
                    assertEquals(1, assets.size());
                    
                    CollibraAsset asset = assets.get(0);
                    // Verify identifier format: siteid > worksheetid
                    assertEquals("site-1 > worksheet-1", asset.getIdentifier().getName());
                    
                    // Verify no attributes (should be null or empty)
                    if (asset.getAttributes() != null) {
                        assertTrue(asset.getAttributes().isEmpty(), "Worksheet should have no attributes");
                    }
                    
                    // Verify relation to workbook with correct relation ID
                    assertNotNull(asset.getRelations());
                    assertTrue(asset.getRelations().containsKey("0195fd0b-f14f-7e72-a382-750d4f3a704e:SOURCE"));
                    
                    // Verify workbook identifier format in relation
                    List<CollibraRelationTarget> targets = asset.getRelations().get("0195fd0b-f14f-7e72-a382-750d4f3a704e:SOURCE");
                    assertNotNull(targets);
                    assertEquals(1, targets.size());
                    assertEquals("site-1 > workbook-1", targets.get(0).getName());
                    
                    return Mono.just(CollibraIngestionResult.success("Worksheet", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestWorksheetsToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(collibraClient).importAssets(anyList(), eq("Worksheet"));
        verify(worksheetRepository).findAllWithWorkbook();
    }

    @Test
    void testDateConversionWithDifferentTimes() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        // Create workbooks with various times to ensure time component is stripped
        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        
        // Test 1: Early morning (00:01)
        TableauWorkbook workbook1 = TableauWorkbook.builder()
                .id(1L)
                .assetId("workbook-1")
                .name("Test Workbook 1")
                .siteId("site-1")
                .tableauCreatedAt(LocalDateTime.of(2024, 1, 15, 0, 1))
                .tableauUpdatedAt(LocalDateTime.of(2024, 1, 15, 0, 1))
                .project(project)
                .statusFlag(StatusFlag.NEW)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
        
        // Test 2: Late night (23:59)
        TableauWorkbook workbook2 = TableauWorkbook.builder()
                .id(2L)
                .assetId("workbook-2")
                .name("Test Workbook 2")
                .siteId("site-1")
                .tableauCreatedAt(LocalDateTime.of(2024, 1, 15, 23, 59))
                .tableauUpdatedAt(LocalDateTime.of(2024, 1, 15, 23, 59))
                .project(project)
                .statusFlag(StatusFlag.NEW)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();

        when(workbookRepository.findAllWithProject()).thenReturn(List.of(workbook1, workbook2));
        when(collibraClient.importAssets(anyList(), eq("Workbook")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    assertEquals(2, assets.size());
                    
                    // Both workbooks should have the same date timestamp despite different times
                    // 2024-01-15T00:00:00Z = 1705276800000
                    String expectedTimestamp = "1705276800000";
                    
                    for (CollibraAsset asset : assets) {
                        List<CollibraAttributeValue> creationDates = asset.getAttributes().get("Document creation date");
                        assertNotNull(creationDates);
                        assertEquals(expectedTimestamp, creationDates.get(0).getValue(),
                            "Date should be normalized to midnight UTC regardless of original time");
                        
                        List<CollibraAttributeValue> modificationDates = asset.getAttributes().get("Document modification date");
                        assertNotNull(modificationDates);
                        assertEquals(expectedTimestamp, modificationDates.get(0).getValue(),
                            "Date should be normalized to midnight UTC regardless of original time");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("Workbook", 2, 2, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestWorkbooksToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(collibraClient).importAssets(anyList(), eq("Workbook"));
    }
    
    @Test
    void testDateConversionWithNullDates() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        
        // Create workbook with null dates
        TableauWorkbook workbook = TableauWorkbook.builder()
                .id(1L)
                .assetId("workbook-1")
                .name("Test Workbook")
                .siteId("site-1")
                .tableauCreatedAt(null)  // Null date
                .tableauUpdatedAt(null)  // Null date
                .project(project)
                .statusFlag(StatusFlag.NEW)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();

        when(workbookRepository.findAllWithProject()).thenReturn(List.of(workbook));
        when(collibraClient.importAssets(anyList(), eq("Workbook")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    
                    // Verify that null dates are not added as attributes
                    // When no attributes are present, getAttributes() returns null
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    if (attributes != null) {
                        assertFalse(attributes.containsKey("Document creation date"),
                            "Null creation date should not be added as attribute");
                        assertFalse(attributes.containsKey("Document modification date"),
                            "Null modification date should not be added as attribute");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("Workbook", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestWorkbooksToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(collibraClient).importAssets(anyList(), eq("Workbook"));
    }

    @Test
    void testIngestProjectsWithDeletion() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.ACTIVE);
        TableauSite site = createTestSite("site-1", "Test Site", "testsite", server, StatusFlag.ACTIVE);
        
        // Create a project to delete
        TableauProject projectToDelete = createTestProjectWithSite("proj-del", "Deleted Project", "site-1", null, site, StatusFlag.DELETED);
        // Create a project to update
        TableauProject projectToUpdate = createTestProjectWithSite("proj-upd", "Updated Project", "site-1", null, site, StatusFlag.UPDATED);

        when(projectRepository.findAllWithSiteAndServer()).thenReturn(List.of(projectToDelete, projectToUpdate));
        when(collibraClient.importAssets(anyList(), eq("Project")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Project", 1, 1, 0, 0, 0)));

        // Mock the deletion flow
        String identifierToDelete = "site-1 > proj-del";
        when(collibraClient.findAssetByIdentifier(eq(identifierToDelete), anyString(), anyString()))
                .thenReturn(Mono.just("uuid-123"));
        when(collibraClient.deleteAsset("uuid-123"))
                .thenReturn(Mono.just(true));

        CollibraIngestionResult result = ingestionService.ingestProjectsToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAssetsDeleted(), "Should have deleted 1 asset");
        
        // Verify import was called with only the updated project
        verify(collibraClient).importAssets(argThat(assets -> assets.size() == 1), eq("Project"));
        // Verify deletion was attempted
        verify(collibraClient).findAssetByIdentifier(eq(identifierToDelete), eq("Tableau Projects"), eq("Tableau Technology"));
        verify(collibraClient).deleteAsset("uuid-123");
    }

    @Test
    void testIngestServersWithDeletion() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getServerDomainName()).thenReturn("Tableau Server");

        // Create a server to delete
        TableauServer serverToDelete = createTestServer("server-del", "Deleted Server", StatusFlag.DELETED);
        // Create a server to update
        TableauServer serverToUpdate = createTestServer("server-upd", "Updated Server", StatusFlag.UPDATED);

        when(serverRepository.findAll()).thenReturn(List.of(serverToDelete, serverToUpdate));
        when(collibraClient.importAssets(anyList(), eq("Server")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Server", 1, 1, 0, 0, 0)));

        // Mock the deletion flow
        String identifierToDelete = "server-del";
        when(collibraClient.findAssetByIdentifier(eq(identifierToDelete), anyString(), anyString()))
                .thenReturn(Mono.just("uuid-456"));
        when(collibraClient.deleteAsset("uuid-456"))
                .thenReturn(Mono.just(true));

        CollibraIngestionResult result = ingestionService.ingestServersToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAssetsDeleted(), "Should have deleted 1 asset");
        
        // Verify deletion was attempted
        verify(collibraClient).findAssetByIdentifier(eq(identifierToDelete), eq("Tableau Server"), eq("Tableau Technology"));
        verify(collibraClient).deleteAsset("uuid-456");
    }

    @Test
    void testIngestProjectsWithDeletion_AssetNotFoundInCollibra() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.ACTIVE);
        TableauSite site = createTestSite("site-1", "Test Site", "testsite", server, StatusFlag.ACTIVE);
        
        // Create a project to delete that doesn't exist in Collibra
        TableauProject projectToDelete = createTestProjectWithSite("proj-del", "Deleted Project", "site-1", null, site, StatusFlag.DELETED);

        when(projectRepository.findAllWithSiteAndServer()).thenReturn(List.of(projectToDelete));
        when(collibraClient.importAssets(anyList(), eq("Project")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Project", 0, 0, 0, 0, 0)));

        // Mock the deletion flow - asset not found
        String identifierToDelete = "site-1 > proj-del";
        when(collibraClient.findAssetByIdentifier(eq(identifierToDelete), anyString(), anyString()))
                .thenReturn(Mono.empty());

        CollibraIngestionResult result = ingestionService.ingestProjectsToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getAssetsDeleted(), "Should have deleted 0 assets (not found in Collibra)");
        
        // Verify search was attempted but delete was not called
        verify(collibraClient).findAssetByIdentifier(eq(identifierToDelete), eq("Tableau Projects"), eq("Tableau Technology"));
        verify(collibraClient, never()).deleteAsset(anyString());
    }

    @Test
    void testIngestWorkbooksWithDeletion() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");

        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        
        // Create a workbook to delete
        TableauWorkbook workbookToDelete = createTestWorkbook("workbook-del", "Deleted Workbook", "site-1", project, StatusFlag.DELETED);

        when(workbookRepository.findAllWithProject()).thenReturn(List.of(workbookToDelete));
        when(collibraClient.importAssets(anyList(), eq("Workbook")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Workbook", 0, 0, 0, 0, 0)));

        // Mock the deletion flow
        String identifierToDelete = "site-1 > workbook-del";
        when(collibraClient.findAssetByIdentifier(eq(identifierToDelete), anyString(), anyString()))
                .thenReturn(Mono.just("uuid-789"));
        when(collibraClient.deleteAsset("uuid-789"))
                .thenReturn(Mono.just(true));

        CollibraIngestionResult result = ingestionService.ingestWorkbooksToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAssetsDeleted(), "Should have deleted 1 asset");
        
        // Verify deletion was attempted
        verify(collibraClient).findAssetByIdentifier(eq(identifierToDelete), eq("Tableau Workbooks"), eq("Tableau Technology"));
        verify(collibraClient).deleteAsset("uuid-789");
    }

    // ======================== Site-Level Ingestion Tests ========================

    @Test
    void testIngestProjectsBySiteToCollibra_NotConfigured() {
        when(collibraClient.isConfigured()).thenReturn(false);

        CollibraIngestionResult result = ingestionService.ingestProjectsBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not configured"));
    }

    @Test
    void testIngestProjectsBySiteToCollibra_EmptyList() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(projectRepository.findAllBySiteIdWithSiteAndServer("site-1")).thenReturn(Collections.emptyList());
        when(collibraClient.importAssets(anyList(), eq("Project")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Project", 0, 0, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestProjectsBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalProcessed());
    }

    @Test
    void testIngestProjectsBySiteToCollibra_WithNewProject() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");
        when(collibraConfig.getSiteDomainName()).thenReturn("Tableau Sites");

        TableauServer server = createTestServer("server-1", "Test Server", StatusFlag.ACTIVE);
        TableauSite site = createTestSite("site-1", "Test Site", "testsite", server, StatusFlag.ACTIVE);
        TableauProject project = createTestProjectWithSite("proj-1", "Test Project", "site-1", null, site, StatusFlag.NEW);

        when(projectRepository.findAllBySiteIdWithSiteAndServer("site-1")).thenReturn(List.of(project));
        when(collibraClient.importAssets(anyList(), eq("Project")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Project", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestProjectsBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        verify(projectRepository).findAllBySiteIdWithSiteAndServer("site-1");
    }

    @Test
    void testIngestWorkbooksBySiteToCollibra_NotConfigured() {
        when(collibraClient.isConfigured()).thenReturn(false);

        CollibraIngestionResult result = ingestionService.ingestWorkbooksBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not configured"));
    }

    @Test
    void testIngestWorkbooksBySiteToCollibra_WithNewWorkbook() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");
        when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");

        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        TableauWorkbook workbook = createTestWorkbook("workbook-1", "Test Workbook", "site-1", project, StatusFlag.NEW);

        when(workbookRepository.findAllBySiteIdWithProject("site-1")).thenReturn(List.of(workbook));
        when(collibraClient.importAssets(anyList(), eq("Workbook")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Workbook", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestWorkbooksBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        verify(workbookRepository).findAllBySiteIdWithProject("site-1");
    }

    @Test
    void testIngestWorksheetsBySiteToCollibra_NotConfigured() {
        when(collibraClient.isConfigured()).thenReturn(false);

        CollibraIngestionResult result = ingestionService.ingestWorksheetsBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not configured"));
    }

    @Test
    void testIngestWorksheetsBySiteToCollibra_WithNewWorksheet() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getWorksheetDomainName()).thenReturn("Tableau Worksheets");
        when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");

        TableauProject project = createTestProject("proj-1", "Test Project", "site-1", null, StatusFlag.ACTIVE);
        TableauWorkbook workbook = createTestWorkbook("workbook-1", "Test Workbook", "site-1", project, StatusFlag.ACTIVE);
        TableauWorksheet worksheet = createTestWorksheet("worksheet-1", "Test Worksheet", "site-1", workbook, StatusFlag.NEW);

        when(worksheetRepository.findAllBySiteIdWithWorkbook("site-1")).thenReturn(List.of(worksheet));
        when(collibraClient.importAssets(anyList(), eq("Worksheet")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Worksheet", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestWorksheetsBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        verify(worksheetRepository).findAllBySiteIdWithWorkbook("site-1");
    }

    @Test
    void testIngestDataSourcesBySiteToCollibra_NotConfigured() {
        when(collibraClient.isConfigured()).thenReturn(false);

        CollibraIngestionResult result = ingestionService.ingestDataSourcesBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not configured"));
    }

    @Test
    void testIngestDataSourcesBySiteToCollibra_WithNewDataSource() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getDatasourceDomainName()).thenReturn("Tableau DataSources");

        TableauDataSource dataSource = createTestDataSource("ds-1", "Test DataSource", "site-1", StatusFlag.NEW);

        when(dataSourceRepository.findBySiteId("site-1")).thenReturn(List.of(dataSource));
        when(collibraClient.importAssets(anyList(), eq("DataSource")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("DataSource", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestDataSourcesBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        verify(dataSourceRepository).findBySiteId("site-1");
    }

    @Test
    void testIngestReportAttributesBySiteToCollibra_NotConfigured() {
        when(collibraClient.isConfigured()).thenReturn(false);

        CollibraIngestionResult result = ingestionService.ingestReportAttributesBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not configured"));
    }

    @Test
    void testIngestReportAttributesBySiteToCollibra_WithNewReportAttribute() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        ReportAttribute reportAttribute = createTestReportAttribute("ra-1", "Test Attribute", "site-1", StatusFlag.NEW);

        when(reportAttributeRepository.findBySiteIdWithRelations("site-1")).thenReturn(List.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestReportAttributesBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        verify(reportAttributeRepository).findBySiteIdWithRelations("site-1");
    }

    @Test
    void testIngestReportAttributeToCollibra_CapitalizesFieldRole() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Test with uppercase field role (as it comes from Tableau API)
        ReportAttribute reportAttribute = createTestReportAttribute("ra-1", "Test Attribute", "site-1", StatusFlag.NEW);
        reportAttribute.setFieldRole("MEASURE");  // Uppercase from API

        when(reportAttributeRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    assertFalse(assets.isEmpty(), "Should have at least one asset");
                    
                    CollibraAsset asset = assets.get(0);
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    
                    if (attributes != null && attributes.containsKey("Role in Report")) {
                        List<CollibraAttributeValue> roleValues = attributes.get("Role in Report");
                        assertFalse(roleValues.isEmpty(), "Role in Report should have a value");
                        
                        String roleValue = roleValues.get(0).getValue();
                        assertEquals("Measure", roleValue, 
                            "Field role should be capitalized: first letter uppercase, rest lowercase");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(1L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(collibraClient).importAssets(anyList(), eq("ReportAttribute"));
    }

    @Test
    void testIngestReportAttributeToCollibra_CapitalizesFieldRole_Dimension() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Test with DIMENSION
        ReportAttribute reportAttribute = createTestReportAttribute("ra-2", "Dimension Field", "site-1", StatusFlag.NEW);
        reportAttribute.setFieldRole("DIMENSION");  // Uppercase from API

        when(reportAttributeRepository.findByIdWithRelations(2L)).thenReturn(Optional.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    
                    if (attributes != null && attributes.containsKey("Role in Report")) {
                        String roleValue = attributes.get("Role in Report").get(0).getValue();
                        assertEquals("Dimension", roleValue, 
                            "DIMENSION should be capitalized to Dimension");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(2L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIngestReportAttributeToCollibra_CapitalizesFieldRole_Lowercase() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Test with lowercase
        ReportAttribute reportAttribute = createTestReportAttribute("ra-3", "Lowercase Field", "site-1", StatusFlag.NEW);
        reportAttribute.setFieldRole("measure");  // lowercase

        when(reportAttributeRepository.findByIdWithRelations(3L)).thenReturn(Optional.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    
                    if (attributes != null && attributes.containsKey("Role in Report")) {
                        String roleValue = attributes.get("Role in Report").get(0).getValue();
                        assertEquals("Measure", roleValue, 
                            "lowercase 'measure' should be capitalized to Measure");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(3L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIngestReportAttributeToCollibra_CapitalizesFieldRole_MixedCase() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Test with mixed case
        ReportAttribute reportAttribute = createTestReportAttribute("ra-4", "Mixed Case Field", "site-1", StatusFlag.NEW);
        reportAttribute.setFieldRole("DiMeNsIoN");  // mixed case

        when(reportAttributeRepository.findByIdWithRelations(4L)).thenReturn(Optional.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    
                    if (attributes != null && attributes.containsKey("Role in Report")) {
                        String roleValue = attributes.get("Role in Report").get(0).getValue();
                        assertEquals("Dimension", roleValue, 
                            "mixed case 'DiMeNsIoN' should be capitalized to Dimension");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(4L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIngestReportAttributeToCollibra_HandlesNullFieldRole() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Test with null field role
        ReportAttribute reportAttribute = createTestReportAttribute("ra-5", "Null Role Field", "site-1", StatusFlag.NEW);
        reportAttribute.setFieldRole(null);  // null

        when(reportAttributeRepository.findByIdWithRelations(5L)).thenReturn(Optional.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    
                    // "Role in Report" attribute should not be added when fieldRole is null
                    if (attributes != null && attributes.containsKey("Role in Report")) {
                        fail("Role in Report should not be present when fieldRole is null");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(5L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIngestReportAttributeToCollibra_HandlesEmptyFieldRole() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Test with empty field role
        ReportAttribute reportAttribute = createTestReportAttribute("ra-6", "Empty Role Field", "site-1", StatusFlag.NEW);
        reportAttribute.setFieldRole("");  // empty

        when(reportAttributeRepository.findByIdWithRelations(6L)).thenReturn(Optional.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    
                    // "Role in Report" attribute should not be added when fieldRole is empty
                    if (attributes != null && attributes.containsKey("Role in Report")) {
                        fail("Role in Report should not be present when fieldRole is empty");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(6L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIngestReportAttributeToCollibra_HandlesSingleCharacter() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Test with single character
        ReportAttribute reportAttribute = createTestReportAttribute("ra-7", "Single Char Field", "site-1", StatusFlag.NEW);
        reportAttribute.setFieldRole("m");  // single character

        when(reportAttributeRepository.findByIdWithRelations(7L)).thenReturn(Optional.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    CollibraAsset asset = assets.get(0);
                    Map<String, List<CollibraAttributeValue>> attributes = asset.getAttributes();
                    
                    if (attributes != null && attributes.containsKey("Role in Report")) {
                        String roleValue = attributes.get("Role in Report").get(0).getValue();
                        assertEquals("M", roleValue, 
                            "single character 'm' should be capitalized to M");
                    }
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributeToCollibra(7L).block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIngestAllBySiteToCollibra_NotConfigured() {
        when(collibraClient.isConfigured()).thenReturn(false);

        CollibraIngestionResult result = ingestionService.ingestAllBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not configured"));
    }

    @Test
    void testIngestAllBySiteToCollibra_Success() {
        when(collibraClient.isConfigured()).thenReturn(true);
        // These stubs might not be called with empty lists, so use lenient()
        lenient().when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        lenient().when(collibraConfig.getProjectDomainName()).thenReturn("Tableau Projects");
        lenient().when(collibraConfig.getWorkbookDomainName()).thenReturn("Tableau Workbooks");
        lenient().when(collibraConfig.getWorksheetDomainName()).thenReturn("Tableau Worksheets");
        lenient().when(collibraConfig.getDatasourceDomainName()).thenReturn("Tableau DataSources");
        lenient().when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Mock empty returns for all repositories
        when(projectRepository.findAllBySiteIdWithSiteAndServer("site-1")).thenReturn(Collections.emptyList());
        when(workbookRepository.findAllBySiteIdWithProject("site-1")).thenReturn(Collections.emptyList());
        when(worksheetRepository.findAllBySiteIdWithWorkbook("site-1")).thenReturn(Collections.emptyList());
        when(dataSourceRepository.findBySiteId("site-1")).thenReturn(Collections.emptyList());
        when(reportAttributeRepository.findBySiteIdWithRelations("site-1")).thenReturn(Collections.emptyList());

        // Mock import responses
        when(collibraClient.importAssets(anyList(), anyString()))
                .thenReturn(Mono.just(CollibraIngestionResult.success("Any", 0, 0, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestAllBySiteToCollibra("site-1").block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("All (Site: site-1)", result.getAssetType());
        assertTrue(result.getMessage().contains("site-1"));
    }

    private TableauDataSource createTestDataSource(String assetId, String name, String siteId, StatusFlag statusFlag) {
        return TableauDataSource.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .siteId(siteId)
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    private ReportAttribute createTestReportAttribute(String assetId, String name, String siteId, StatusFlag statusFlag) {
        return ReportAttribute.builder()
                .id(1L)
                .assetId(assetId)
                .name(name)
                .siteId(siteId)
                .statusFlag(statusFlag)
                .createdTimestamp(LocalDateTime.now())
                .lastUpdatedTimestamp(LocalDateTime.now())
                .build();
    }

    // ======================== Report Attribute Ingestion Tests ========================

    @Test
    void testIngestReportAttributesToCollibra_UsesEagerFetchMethod() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Create test data with worksheet relationship
        ReportAttribute reportAttribute = createTestReportAttribute("ra-1", "Test Attribute", "site-1", StatusFlag.NEW);
        reportAttribute.setWorksheetId("ws-1");

        // Use findAllWithRelations which eagerly fetches relationships
        when(reportAttributeRepository.findAllWithRelations()).thenReturn(List.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenReturn(Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0)));

        CollibraIngestionResult result = ingestionService.ingestReportAttributesToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalProcessed());
        // Verify that findAllWithRelations was called (not findAll)
        verify(reportAttributeRepository).findAllWithRelations();
        verify(reportAttributeRepository, never()).findAll();
    }

    @Test
    void testReportAttributeIdentifierIncludesWorksheetId() {
        when(collibraClient.isConfigured()).thenReturn(true);
        when(collibraConfig.getCommunityName()).thenReturn("Tableau Technology");
        when(collibraConfig.getReportAttributeDomainName()).thenReturn("Tableau Report Attributes");

        // Create test data with worksheet ID
        ReportAttribute reportAttribute = createTestReportAttribute("ra-1", "Test Attribute", "site-1", StatusFlag.NEW);
        reportAttribute.setWorksheetId("ws-123");

        when(reportAttributeRepository.findAllWithRelations()).thenReturn(List.of(reportAttribute));
        when(collibraClient.importAssets(anyList(), eq("ReportAttribute")))
                .thenAnswer(invocation -> {
                    List<CollibraAsset> assets = invocation.getArgument(0);
                    assertFalse(assets.isEmpty(), "Should have at least one asset");
                    
                    CollibraAsset asset = assets.get(0);
                    String identifierName = asset.getIdentifier().getName();
                    
                    // Verify identifier includes siteId, worksheetId and assetId
                    // Format: siteid > worksheetid > assetid
                    assertTrue(identifierName.contains("site-1"), 
                        "Identifier should contain siteId");
                    assertTrue(identifierName.contains("ws-123"), 
                        "Identifier should contain worksheetId");
                    assertTrue(identifierName.contains("ra-1"), 
                        "Identifier should contain assetId");
                    assertEquals("site-1 > ws-123 > ra-1", identifierName,
                        "Identifier should be in format: siteid > worksheetid > assetid");
                    
                    return Mono.just(CollibraIngestionResult.success("ReportAttribute", 1, 1, 0, 0, 0));
                });

        CollibraIngestionResult result = ingestionService.ingestReportAttributesToCollibra().block();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }
}
