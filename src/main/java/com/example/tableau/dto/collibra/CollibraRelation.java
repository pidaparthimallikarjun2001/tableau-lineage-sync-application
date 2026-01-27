package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO representing a relation between two Collibra assets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraRelation {

    /**
     * Name of the relation type in Collibra.
     */
    private String relationTypeName;

    /**
     * Full name of the source asset.
     */
    private String sourceFullName;

    /**
     * Full name of the target asset.
     */
    private String targetFullName;

    /**
     * External entity ID of the source asset.
     */
    private String sourceExternalEntityId;

    /**
     * External entity ID of the target asset.
     */
    private String targetExternalEntityId;

    /**
     * Asset type name of the source asset.
     */
    private String sourceAssetTypeName;

    /**
     * Asset type name of the target asset.
     */
    private String targetAssetTypeName;

    /**
     * Domain name of the source asset.
     */
    private String sourceDomainName;

    /**
     * Domain name of the target asset.
     */
    private String targetDomainName;

    /**
     * Community name of the source asset.
     */
    private String sourceCommunityName;

    /**
     * Community name of the target asset.
     */
    private String targetCommunityName;
}
