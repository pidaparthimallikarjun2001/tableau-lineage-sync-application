package com.example.tableau.service;

import com.example.tableau.config.CollibraApiConfig;
import com.example.tableau.dto.collibra.CollibraAsset;
import com.example.tableau.dto.collibra.CollibraIngestionResult;
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
}
