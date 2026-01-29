package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * DTO representing an asset to be ingested into Collibra.
 * Uses the Collibra Import API JSON format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraAsset {

    /**
     * Resource type - always "Asset" for assets.
     */
    @Builder.Default
    private String resourceType = "Asset";

    /**
     * Type of the asset.
     */
    private CollibraType type;

    /**
     * Display name of the asset (human-readable name from Tableau).
     */
    private String displayName;

    /**
     * Identifier object containing unique name, domain, and community.
     */
    private CollibraIdentifier identifier;

    /**
     * Map of attributes for the asset.
     * Key: Attribute name (e.g., "Description", "URL")
     * Value: List of attribute values
     */
    private Map<String, List<CollibraAttributeValue>> attributes;

    /**
     * Map of relations for the asset.
     * Key: Relation type (e.g., "relationid:SOURCE", "relationid:TARGET")
     * Value: List of relation targets
     */
    private Map<String, List<CollibraRelationTarget>> relations;

    /**
     * Creates an identifier name in the format: assetid > assetname
     */
    public static String createIdentifierName(String assetId, String assetName) {
        String safeAssetId = assetId != null ? assetId : "unknown";
        String safeAssetName = assetName != null ? assetName : "unnamed";
        return safeAssetId + " > " + safeAssetName;
    }

    /**
     * Creates an identifier name for server-level assets in the format: serverid
     */
    public static String createServerIdentifierName(String assetId, String assetName) {
        String safeAssetId = assetId != null ? assetId : "unknown";
        return safeAssetId;
    }

    /**
     * Creates an identifier name for site assets in the format: siteid
     */
    public static String createSiteIdentifierName(String siteId, String siteName) {
        String safeSiteId = siteId != null ? siteId : "unknown";
        return safeSiteId;
    }

    /**
     * Creates an identifier name for project assets in the format: siteid > projectid
     */
    public static String createProjectIdentifierName(String siteId, String assetId, String assetName) {
        String safeSiteId = siteId != null ? siteId : "unknown";
        String safeAssetId = assetId != null ? assetId : "unknown";
        return safeSiteId + " > " + safeAssetId;
    }

    /**
     * Creates an identifier name for workbook assets in the format: siteid > workbookid
     */
    public static String createWorkbookIdentifierName(String siteId, String assetId, String assetName) {
        String safeSiteId = siteId != null ? siteId : "unknown";
        String safeAssetId = assetId != null ? assetId : "unknown";
        return safeSiteId + " > " + safeAssetId;
    }

    /**
     * Creates an identifier name for worksheet assets in the format: siteid > worksheetid
     */
    public static String createWorksheetIdentifierName(String siteId, String assetId, String assetName) {
        String safeSiteId = siteId != null ? siteId : "unknown";
        String safeAssetId = assetId != null ? assetId : "unknown";
        return safeSiteId + " > " + safeAssetId;
    }

    /**
     * Creates an identifier name for report attribute assets in the format: siteid > worksheetid > assetid
     * The worksheetId is required because the same field (assetId) can appear in multiple worksheets,
     * so the identifier must include worksheetId to ensure uniqueness.
     */
    public static String createReportAttributeIdentifierName(String siteId, String worksheetId, String assetId, String assetName) {
        String safeSiteId = siteId != null ? siteId : "unknown";
        String safeWorksheetId = worksheetId != null ? worksheetId : "unknown";
        String safeAssetId = assetId != null ? assetId : "unknown";
        return safeSiteId + " > " + safeWorksheetId + " > " + safeAssetId;
    }

    /**
     * @deprecated Use createReportAttributeIdentifierName(siteId, worksheetId, assetId, assetName) instead.
     * This method is kept for backward compatibility but produces non-unique identifiers.
     */
    @Deprecated
    public static String createReportAttributeIdentifierName(String siteId, String assetId, String assetName) {
        String safeSiteId = siteId != null ? siteId : "unknown";
        String safeAssetId = assetId != null ? assetId : "unknown";
        return safeSiteId + " > " + safeAssetId;
    }

    /**
     * Creates a full name in the format: siteid>assetid>assetname (for backward compatibility)
     * @deprecated Use createIdentifierName instead
     */
    @Deprecated
    public static String createFullName(String siteId, String assetId, String assetName) {
        String safeSiteId = siteId != null ? siteId : "default";
        String safeAssetId = assetId != null ? assetId : "unknown";
        String safeAssetName = assetName != null ? assetName : "unnamed";
        return safeSiteId + ">" + safeAssetId + ">" + safeAssetName;
    }

    /**
     * Creates a full name for server-level assets (no site context). (for backward compatibility)
     * @deprecated Use createServerIdentifierName instead
     */
    @Deprecated
    public static String createServerFullName(String assetId, String assetName) {
        String safeAssetId = assetId != null ? assetId : "unknown";
        String safeAssetName = assetName != null ? assetName : "unnamed";
        return "server>" + safeAssetId + ">" + safeAssetName;
    }
}
