package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO representing the result of a Collibra ingestion operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraIngestionResult {

    /**
     * Type of assets being ingested (e.g., "Server", "Workbook").
     */
    private String assetType;

    /**
     * Total number of assets processed.
     */
    private int totalProcessed;

    /**
     * Number of new assets created in Collibra.
     */
    private int assetsCreated;

    /**
     * Number of existing assets updated in Collibra.
     */
    private int assetsUpdated;

    /**
     * Number of assets marked as deleted in Collibra.
     */
    private int assetsDeleted;

    /**
     * Number of assets that were skipped (no changes).
     */
    private int assetsSkipped;

    /**
     * Number of relations created.
     */
    private int relationsCreated;

    /**
     * Whether the ingestion was successful.
     */
    private boolean success;

    /**
     * Status message.
     */
    private String message;

    /**
     * Job ID returned by Collibra (for async jobs).
     */
    private String jobId;

    /**
     * Timestamp of the operation.
     */
    private LocalDateTime timestamp;

    /**
     * List of errors encountered during ingestion.
     */
    private List<String> errors;

    /**
     * Additional details about the ingestion.
     */
    private Map<String, Object> details;

    public static CollibraIngestionResult success(String assetType, int total, int created, int updated, int deleted, int skipped) {
        return CollibraIngestionResult.builder()
                .assetType(assetType)
                .totalProcessed(total)
                .assetsCreated(created)
                .assetsUpdated(updated)
                .assetsDeleted(deleted)
                .assetsSkipped(skipped)
                .success(true)
                .message("Collibra ingestion completed successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static CollibraIngestionResult failure(String assetType, String errorMessage) {
        return CollibraIngestionResult.builder()
                .assetType(assetType)
                .success(false)
                .message(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static CollibraIngestionResult notConfigured() {
        return CollibraIngestionResult.builder()
                .success(false)
                .message("Collibra integration is not configured. Please set collibra.base-url, collibra.username, and collibra.password.")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
