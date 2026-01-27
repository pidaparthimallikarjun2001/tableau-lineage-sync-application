package com.example.tableau.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Collibra API settings.
 * Manages Collibra API client configuration including base URL, authentication details,
 * proxy settings, and domain mappings for different Tableau asset types.
 */
@Configuration
public class CollibraApiConfig {

    @Value("${collibra.base-url:}")
    private String baseUrl;

    @Value("${collibra.username:}")
    private String username;

    @Value("${collibra.password:}")
    private String password;

    @Value("${collibra.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${collibra.proxy.host:}")
    private String proxyHost;

    @Value("${collibra.proxy.port:8080}")
    private int proxyPort;

    @Value("${collibra.proxy.username:}")
    private String proxyUsername;

    @Value("${collibra.proxy.password:}")
    private String proxyPassword;

    @Value("${collibra.community.name:Tableau Technology}")
    private String communityName;

    @Value("${collibra.domain.server:Tableau Server}")
    private String serverDomainName;

    @Value("${collibra.domain.site:Tableau Site}")
    private String siteDomainName;

    @Value("${collibra.domain.project:Tableau Project}")
    private String projectDomainName;

    @Value("${collibra.domain.workbook:Tableau Workbook}")
    private String workbookDomainName;

    @Value("${collibra.domain.worksheet:Tableau Worksheet}")
    private String worksheetDomainName;

    @Value("${collibra.domain.datasource:Tableau Data Sources}")
    private String datasourceDomainName;

    @Value("${collibra.domain.reportattribute:Tableau Report Attribute}")
    private String reportAttributeDomainName;

    @Value("${collibra.connection.timeout:30000}")
    private int connectionTimeout;

    @Value("${collibra.read.timeout:60000}")
    private int readTimeout;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public String getCommunityName() {
        return communityName;
    }

    public String getServerDomainName() {
        return serverDomainName;
    }

    public String getSiteDomainName() {
        return siteDomainName;
    }

    public String getProjectDomainName() {
        return projectDomainName;
    }

    public String getWorkbookDomainName() {
        return workbookDomainName;
    }

    public String getWorksheetDomainName() {
        return worksheetDomainName;
    }

    public String getDatasourceDomainName() {
        return datasourceDomainName;
    }

    public String getReportAttributeDomainName() {
        return reportAttributeDomainName;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Get the REST API URL for assets
     */
    public String getAssetsApiUrl() {
        return baseUrl + "/rest/2.0/assets/bulk";
    }

    /**
     * Get the Import API URL for JSON multipart upload
     */
    public String getImportApiUrl() {
        return baseUrl + "/rest/2.0/import/json-job";
    }

    /**
     * Get the Communities API URL
     */
    public String getCommunitiesApiUrl() {
        return baseUrl + "/rest/2.0/communities";
    }

    /**
     * Get the Domains API URL
     */
    public String getDomainsApiUrl() {
        return baseUrl + "/rest/2.0/domains";
    }

    /**
     * Get the Asset Types API URL
     */
    public String getAssetTypesApiUrl() {
        return baseUrl + "/rest/2.0/assetTypes";
    }

    /**
     * Get the Relations API URL
     */
    public String getRelationsApiUrl() {
        return baseUrl + "/rest/2.0/relations/bulk";
    }

    /**
     * Check if Collibra integration is configured
     */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isEmpty() 
               && username != null && !username.isEmpty()
               && password != null && !password.isEmpty();
    }

    /**
     * Get the domain name for a given asset type
     */
    public String getDomainNameForAssetType(String assetType) {
        return switch (assetType.toUpperCase()) {
            case "SERVER" -> serverDomainName;
            case "SITE" -> siteDomainName;
            case "PROJECT" -> projectDomainName;
            case "WORKBOOK" -> workbookDomainName;
            case "WORKSHEET" -> worksheetDomainName;
            case "DATASOURCE" -> datasourceDomainName;
            case "REPORTATTRIBUTE" -> reportAttributeDomainName;
            default -> "Tableau Technology";
        };
    }
}
