package com.example.tableau.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration class for Tableau API settings.
 * Manages Tableau API client configuration including base URL, authentication details, and GraphQL endpoint.
 * Dynamically updates with the active site and authentication token.
 */
@Configuration
public class TableauApiConfig {

    @Value("${tableau.base-url}")
    private String baseUrl;

    @Value("${tableau.api-version:3.17}")
    private String apiVersion;

    @Value("${tableau.auth-mode:PAT}")
    private String authMode;

    @Value("${tableau.pat.name:}")
    private String patName;

    @Value("${tableau.pat.secret:}")
    private String patSecret;

    @Value("${tableau.username:}")
    private String username;

    @Value("${tableau.password:}")
    private String password;

    @Value("${tableau.default-site-id:}")
    private String defaultSiteId;

    private final AtomicReference<String> currentSiteId = new AtomicReference<>();
    private final AtomicReference<String> currentSiteContentUrl = new AtomicReference<>();
    private final AtomicReference<String> currentAuthToken = new AtomicReference<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getAuthMode() {
        return authMode;
    }

    public String getPatName() {
        return patName;
    }

    public String getPatSecret() {
        return patSecret;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDefaultSiteId() {
        return defaultSiteId;
    }

    /**
     * Get the REST API URL
     */
    public String getRestApiUrl() {
        return baseUrl + "/api/" + apiVersion;
    }

    /**
     * Get the GraphQL API URL (Metadata API)
     * Note: GraphQL API operates at the site level
     */
    public String getGraphQlUrl() {
        return baseUrl + "/api/metadata/graphql";
    }

    /**
     * Get the sign-in URL
     */
    public String getSignInUrl() {
        return getRestApiUrl() + "/auth/signin";
    }

    /**
     * Get the sign-out URL
     */
    public String getSignOutUrl() {
        return getRestApiUrl() + "/auth/signout";
    }

    /**
     * Get the switch site URL
     */
    public String getSwitchSiteUrl() {
        return getRestApiUrl() + "/auth/switchSite";
    }

    /**
     * Get the current site ID
     */
    public String getCurrentSiteId() {
        String siteId = currentSiteId.get();
        return siteId != null ? siteId : defaultSiteId;
    }

    /**
     * Set the current site ID
     */
    public void setCurrentSiteId(String siteId) {
        currentSiteId.set(siteId);
    }

    /**
     * Get the current site content URL
     */
    public String getCurrentSiteContentUrl() {
        return currentSiteContentUrl.get();
    }

    /**
     * Set the current site content URL
     */
    public void setCurrentSiteContentUrl(String contentUrl) {
        currentSiteContentUrl.set(contentUrl);
    }

    /**
     * Get the current authentication token
     */
    public String getCurrentAuthToken() {
        return currentAuthToken.get();
    }

    /**
     * Set the current authentication token
     */
    public void setCurrentAuthToken(String token) {
        currentAuthToken.set(token);
    }

    /**
     * Clear the current session
     */
    public void clearSession() {
        currentSiteId.set(null);
        currentSiteContentUrl.set(null);
        currentAuthToken.set(null);
    }
}
