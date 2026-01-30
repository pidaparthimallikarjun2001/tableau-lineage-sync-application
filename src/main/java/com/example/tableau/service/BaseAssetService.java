package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.enums.CollibraSyncStatus;
import com.example.tableau.enums.StatusFlag;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Base service class providing common functionality for asset services.
 */
public abstract class BaseAssetService {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    /** Delimiter used for composite keys */
    protected static final String COMPOSITE_KEY_DELIMITER = "|";
    
    /** Custom SQL asset ID prefix */
    protected static final String CUSTOM_SQL_PREFIX = "custom-sql-";
    
    /** ISO date time format length for parsing */
    private static final int ISO_LOCAL_DATE_TIME_LENGTH = 19;

    /**
     * Generate a hash of the metadata for change detection.
     */
    protected String generateMetadataHash(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (String field : fields) {
                sb.append(field != null ? field : "");
                sb.append(COMPOSITE_KEY_DELIMITER);
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple string hash
            StringBuilder sb = new StringBuilder();
            for (String field : fields) {
                sb.append(field != null ? field : "");
            }
            return String.valueOf(sb.toString().hashCode());
        }
    }

    /**
     * Determine the status flag based on comparison of existing and new data.
     */
    protected StatusFlag determineStatusFlag(String existingHash, String newHash, StatusFlag currentStatus) {
        if (existingHash == null) {
            return StatusFlag.NEW;
        }
        if (!existingHash.equals(newHash)) {
            return StatusFlag.UPDATED;
        }
        // Hash is the same - mark as ACTIVE (no changes)
        if (currentStatus == StatusFlag.NEW || currentStatus == StatusFlag.UPDATED) {
            return StatusFlag.ACTIVE;
        }
        return currentStatus;
    }

    /**
     * Determine the appropriate CollibraSyncStatus based on the new StatusFlag and current CollibraSyncStatus.
     * This ensures that when an asset is updated or deleted in the database, the CollibraSyncStatus
     * is updated to reflect that it needs to be re-synced to Collibra.
     * 
     * @param newStatusFlag the new status flag being set
     * @param currentCollibraSyncStatus the current Collibra sync status
     * @return the appropriate CollibraSyncStatus
     */
    protected CollibraSyncStatus determineCollibraSyncStatus(StatusFlag newStatusFlag, CollibraSyncStatus currentCollibraSyncStatus) {
        if (currentCollibraSyncStatus == null) {
            currentCollibraSyncStatus = CollibraSyncStatus.NOT_SYNCED;
        }
        
        switch (newStatusFlag) {
            case NEW:
                // New assets start as NOT_SYNCED
                return CollibraSyncStatus.NOT_SYNCED;
            case UPDATED:
                // If previously synced, mark as pending update; otherwise keep current status
                if (currentCollibraSyncStatus == CollibraSyncStatus.SYNCED) {
                    return CollibraSyncStatus.PENDING_UPDATE;
                }
                // If already NOT_SYNCED or PENDING_SYNC, it just needs initial sync
                return currentCollibraSyncStatus == CollibraSyncStatus.SYNCED 
                    ? CollibraSyncStatus.PENDING_UPDATE 
                    : currentCollibraSyncStatus;
            case DELETED:
                // If previously synced, mark as pending delete; otherwise keep current status
                if (currentCollibraSyncStatus == CollibraSyncStatus.SYNCED || 
                    currentCollibraSyncStatus == CollibraSyncStatus.PENDING_UPDATE) {
                    return CollibraSyncStatus.PENDING_DELETE;
                }
                return currentCollibraSyncStatus;
            case ACTIVE:
            default:
                // Active assets keep their current Collibra sync status
                return currentCollibraSyncStatus;
        }
    }

    /**
     * Create a success result for ingestion.
     */
    protected IngestionResult createSuccessResult(String assetType, int total, int newRecs, int updated, int deleted, int unchanged) {
        return IngestionResult.success(assetType, total, newRecs, updated, deleted, unchanged);
    }

    /**
     * Create a failure result for ingestion.
     */
    protected IngestionResult createFailureResult(String assetType, String errorMessage) {
        return IngestionResult.failure(assetType, errorMessage);
    }

    /**
     * Safe string extraction from JSON (returns null if empty).
     */
    protected String safeString(String value) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }

    /**
     * Safe string extraction with default value.
     */
    protected String safeStringOrDefault(String value, String defaultValue) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
    
    /**
     * Extract asset ID from a JSON node, preferring luid over id.
     * This handles the common pattern in Tableau APIs where assets have both luid and id fields.
     */
    protected String extractAssetId(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String luid = node.path("luid").asText(null);
        if (luid != null && !luid.isEmpty()) {
            return luid;
        }
        return node.path("id").asText(null);
    }
    
    /**
     * Create a composite key from multiple parts.
     */
    protected String createCompositeKey(String... parts) {
        return String.join(COMPOSITE_KEY_DELIMITER, parts);
    }
    
    /**
     * Parse date time string from Tableau API response.
     * Handles ISO 8601 format and common variations.
     */
    protected LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                // Try parsing with just the date/time portion (truncate timezone if present)
                String truncated = dateStr.length() > ISO_LOCAL_DATE_TIME_LENGTH 
                        ? dateStr.substring(0, ISO_LOCAL_DATE_TIME_LENGTH) 
                        : dateStr;
                return LocalDateTime.parse(truncated, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                log.warn("Could not parse date: {}", dateStr);
                return null;
            }
        }
    }
}
