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
     * External entity ID - Used to uniquely identify the asset.
     * Format: siteid>assetid>assetname
     */
    private String externalEntityId;

    /**
     * Full name of the asset (unique identifier).
     * Format: siteid>assetid>assetname
     */
    private String fullName;

    /**
     * Display name of the asset (human-readable name from Tableau).
     */
    private String displayName;

    /**
     * Name of the asset type in Collibra (e.g., "Tableau Server", "Tableau Workbook").
     */
    private String assetTypeName;

    /**
     * Name of the domain where the asset should be created.
     */
    private String domainName;

    /**
     * Name of the community where the domain exists.
     */
    private String communityName;

    /**
     * Status of the asset (e.g., "Approved", "Candidate").
     */
    private String status;

    /**
     * List of attributes for the asset.
     */
    private List<CollibraAttribute> attributes;

    /**
     * Relations where this asset is the source.
     */
    private List<CollibraRelation> sourceRelations;

    /**
     * Relations where this asset is the target.
     */
    private List<CollibraRelation> targetRelations;

    /**
     * Additional identifier for the asset.
     */
    private String identifier;

    /**
     * Creates a full name in the format: siteid>assetid>assetname
     */
    public static String createFullName(String siteId, String assetId, String assetName) {
        String safeSiteId = siteId != null ? siteId : "default";
        String safeAssetId = assetId != null ? assetId : "unknown";
        String safeAssetName = assetName != null ? assetName : "unnamed";
        return safeSiteId + ">" + safeAssetId + ">" + safeAssetName;
    }

    /**
     * Creates a full name for server-level assets (no site context).
     */
    public static String createServerFullName(String assetId, String assetName) {
        String safeAssetId = assetId != null ? assetId : "unknown";
        String safeAssetName = assetName != null ? assetName : "unnamed";
        return "server>" + safeAssetId + ">" + safeAssetName;
    }
}
