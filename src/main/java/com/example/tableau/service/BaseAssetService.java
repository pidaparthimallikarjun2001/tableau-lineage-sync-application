package com.example.tableau.service;

import com.example.tableau.dto.IngestionResult;
import com.example.tableau.enums.StatusFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Base service class providing common functionality for asset services.
 */
public abstract class BaseAssetService {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Generate a hash of the metadata for change detection.
     */
    protected String generateMetadataHash(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (String field : fields) {
                sb.append(field != null ? field : "");
                sb.append("|");
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
}
