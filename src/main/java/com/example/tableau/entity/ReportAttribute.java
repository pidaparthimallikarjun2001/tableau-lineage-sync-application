package com.example.tableau.entity;

import com.example.tableau.enums.StatusFlag;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing a Report Attribute (Sheet Field Instance).
 * This represents a field used in a worksheet with its source and calculation information.
 */
@Entity
@Table(name = "report_attribute",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId", "worksheetId", "siteId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier from Tableau (LUID)
     */
    @Column(name = "asset_id", nullable = false, length = 128)
    private String assetId;

    /**
     * Worksheet ID for composite unique constraint
     */
    @Column(name = "worksheet_id", length = 128)
    private String worksheetId;

    /**
     * Site ID for composite unique constraint
     */
    @Column(name = "site_id", length = 128)
    private String siteId;

    /**
     * Name of the field
     */
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    /**
     * Data type of the field
     */
    @Column(name = "data_type", length = 64)
    private String dataType;

    /**
     * Role of the field (dimension, measure, etc.)
     */
    @Column(name = "field_role", length = 64)
    private String fieldRole;

    /**
     * Whether this is a calculated field
     */
    @Column(name = "is_calculated")
    @Builder.Default
    private Boolean isCalculated = false;

    /**
     * Calculation logic/formula for calculated fields
     */
    @Column(name = "calculation_logic", columnDefinition = "TEXT")
    private String calculationLogic;

    /**
     * Source data source ID
     */
    @Column(name = "source_datasource_id", length = 128)
    private String sourceDatasourceId;

    /**
     * Source data source name
     */
    @Column(name = "source_datasource_name", length = 512)
    private String sourceDatasourceName;

    /**
     * Source column name (for direct mappings)
     */
    @Column(name = "source_column_name", length = 512)
    private String sourceColumnName;

    /**
     * Source table name
     */
    @Column(name = "source_table_name", length = 512)
    private String sourceTableName;

    /**
     * Complete lineage information as JSON
     */
    @Column(name = "lineage_info", columnDefinition = "TEXT")
    private String lineageInfo;

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
     * Parent worksheet
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worksheet_fk_id")
    private TableauWorksheet worksheet;

    /**
     * Source data source
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_fk_id")
    private TableauDataSource dataSource;

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
