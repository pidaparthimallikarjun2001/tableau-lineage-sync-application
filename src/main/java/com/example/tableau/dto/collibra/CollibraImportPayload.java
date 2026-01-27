package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * DTO representing the JSON import payload for Collibra.
 * This is the root object for the JSON multipart upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraImportPayload {

    /**
     * List of assets to import/update.
     */
    private List<CollibraAsset> assets;

    /**
     * List of relations to import/update.
     */
    private List<CollibraRelation> relations;

    /**
     * Whether to send notification emails.
     */
    @Builder.Default
    private Boolean sendNotification = false;

    /**
     * Whether to continue on error.
     */
    @Builder.Default
    private Boolean continueOnError = true;

    /**
     * Import strategy for assets that already exist.
     * Options: UPDATE, SKIP, ERROR
     */
    @Builder.Default
    private String existingAssetPolicy = "UPDATE";

    /**
     * Import strategy for relations that already exist.
     * Options: UPDATE, SKIP, ERROR
     */
    @Builder.Default
    private String existingRelationPolicy = "UPDATE";
}
