package com.example.tableau.entity;

import com.example.tableau.enums.SourceType;
import com.example.tableau.enums.StatusFlag;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Tableau Data Source.
 * This includes both direct data sources and custom SQL data sources.
 */
@Entity
@Table(name = "tableau_datasource",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId", "siteId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableauDataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier from Tableau (LUID)
     */
    @Column(name = "asset_id", nullable = false, length = 128)
    private String assetId;

    /**
     * Site ID for composite unique constraint
     */
    @Column(name = "site_id", length = 128)
    private String siteId;

    /**
     * Name of the data source
     */
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    /**
     * Type of data source (DIRECT_IMPORT, CUSTOM_SQL, PUBLISHED, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 32)
    @Builder.Default
    private SourceType sourceType = SourceType.OTHER;

    /**
     * Description of the data source
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Owner username
     */
    @Column(name = "owner", length = 256)
    private String owner;

    /**
     * Owner ID
     */
    @Column(name = "owner_id", length = 128)
    private String ownerId;

    /**
     * Connection type (e.g., postgres, mysql, snowflake)
     */
    @Column(name = "connection_type", length = 128)
    private String connectionType;

    /**
     * Table name for direct imports
     */
    @Column(name = "table_name", length = 512)
    private String tableName;

    /**
     * Schema name
     */
    @Column(name = "schema_name", length = 256)
    private String schemaName;

    /**
     * Database name
     */
    @Column(name = "database_name", length = 256)
    private String databaseName;

    /**
     * Server/host for the connection
     */
    @Column(name = "server_name", length = 512)
    private String serverName;

    /**
     * Custom SQL query (for CUSTOM_SQL source type)
     */
    @Column(name = "custom_sql_query", columnDefinition = "TEXT")
    private String customSqlQuery;

    /**
     * Whether the data source is certified
     */
    @Column(name = "is_certified")
    @Builder.Default
    private Boolean isCertified = false;

    /**
     * Whether this is a published data source
     */
    @Column(name = "is_published")
    @Builder.Default
    private Boolean isPublished = false;

    /**
     * Content URL for the data source
     */
    @Column(name = "content_url", length = 1024)
    private String contentUrl;

    /**
     * Status flag for change tracking
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_flag", nullable = false, length = 20)
    @Builder.Default
    private StatusFlag statusFlag = StatusFlag.NEW;

    /**
     * Hash of metadata for change detection
     */
    @Column(name = "metadata_hash", length = 128)
    private String metadataHash;

    /**
     * Timestamp when the record was created
     */
    @Column(name = "created_timestamp", nullable = false)
    private LocalDateTime createdTimestamp;

    /**
     * Timestamp when the record was last updated
     */
    @Column(name = "last_updated_timestamp", nullable = false)
    private LocalDateTime lastUpdatedTimestamp;

    /**
     * Parent workbook (if embedded data source)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workbook_fk_id")
    private TableauWorkbook workbook;

    /**
     * List of report attributes that use this data source
     */
    @OneToMany(mappedBy = "dataSource", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<ReportAttribute> reportAttributes = new ArrayList<>();

    /**
     * Upstream tables information as JSON
     */
    @Column(name = "upstream_tables", columnDefinition = "TEXT")
    private String upstreamTables;

    /**
     * Calculated fields and their formulas as JSON
     */
    @Column(name = "calculated_fields", columnDefinition = "TEXT")
    private String calculatedFields;

    @PrePersist
    protected void onCreate() {
        createdTimestamp = LocalDateTime.now();
        lastUpdatedTimestamp = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedTimestamp = LocalDateTime.now();
    }
}
