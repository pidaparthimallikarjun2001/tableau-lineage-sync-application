package com.example.tableau.dto;

import com.example.tableau.enums.StatusFlag;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for ingestion result summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionResult {

    private String assetType;
    private int totalProcessed;
    private int newRecords;
    private int updatedRecords;
    private int deletedRecords;
    private int unchangedRecords;
    private boolean success;
    private String message;
    private LocalDateTime timestamp;

    public static IngestionResult success(String assetType, int total, int newRecs, int updated, int deleted, int unchanged) {
        return IngestionResult.builder()
                .assetType(assetType)
                .totalProcessed(total)
                .newRecords(newRecs)
                .updatedRecords(updated)
                .deletedRecords(deleted)
                .unchangedRecords(unchanged)
                .success(true)
                .message("Ingestion completed successfully")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static IngestionResult failure(String assetType, String errorMessage) {
        return IngestionResult.builder()
                .assetType(assetType)
                .success(false)
                .message(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
