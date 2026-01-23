package com.example.tableau.exception;

/**
 * Custom exception for data ingestion related errors.
 */
public class DataIngestionException extends RuntimeException {

    private final String assetType;
    private final String assetId;

    public DataIngestionException(String message) {
        super(message);
        this.assetType = null;
        this.assetId = null;
    }

    public DataIngestionException(String message, Throwable cause) {
        super(message, cause);
        this.assetType = null;
        this.assetId = null;
    }

    public DataIngestionException(String message, String assetType, String assetId) {
        super(message);
        this.assetType = assetType;
        this.assetId = assetId;
    }

    public DataIngestionException(String message, String assetType, String assetId, Throwable cause) {
        super(message, cause);
        this.assetType = assetType;
        this.assetId = assetId;
    }

    public String getAssetType() {
        return assetType;
    }

    public String getAssetId() {
        return assetId;
    }
}
