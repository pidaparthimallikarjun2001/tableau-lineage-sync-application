package com.example.tableau.enums;

/**
 * Enum representing the type of data source in Tableau.
 */
public enum SourceType {
    /**
     * Direct import from a database table
     */
    DIRECT_IMPORT,
    
    /**
     * Custom SQL query used to define the data source
     */
    CUSTOM_SQL,
    
    /**
     * Published data source reference
     */
    PUBLISHED,
    
    /**
     * File-based data source (Excel, CSV, etc.)
     */
    FILE_BASED,
    
    /**
     * Unknown or other source type
     */
    OTHER
}
