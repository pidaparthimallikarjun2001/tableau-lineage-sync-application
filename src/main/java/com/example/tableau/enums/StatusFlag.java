package com.example.tableau.enums;

/**
 * Enum representing the status of an asset in the database.
 * Used for change tracking between Tableau and the local database.
 */
public enum StatusFlag {
    /**
     * New record found in Tableau that is not in the database
     */
    NEW,
    
    /**
     * Existing record that has been updated in Tableau
     */
    UPDATED,
    
    /**
     * Record present in the database but no longer found in Tableau (soft delete)
     */
    DELETED,
    
    /**
     * Record is active and synchronized with Tableau
     */
    ACTIVE
}
