package com.example.tableau.enums;

/**
 * Enum representing the synchronization status of an asset with Collibra.
 * Used for tracking whether assets from the database have been synchronized to Collibra
 * and managing incremental updates.
 */
public enum CollibraSyncStatus {
    /**
     * Asset has never been synced to Collibra.
     * This is the default state for new assets.
     */
    NOT_SYNCED,
    
    /**
     * Asset is pending synchronization to Collibra.
     * This indicates the asset is queued for import.
     */
    PENDING_SYNC,
    
    /**
     * Asset has been successfully synced to Collibra.
     * No changes are pending.
     */
    SYNCED,
    
    /**
     * Asset was previously synced but has been updated in the database.
     * The updated asset needs to be re-synced to Collibra.
     */
    PENDING_UPDATE,
    
    /**
     * Asset was previously synced but has been marked for deletion.
     * The asset needs to be deleted from Collibra.
     */
    PENDING_DELETE
}
