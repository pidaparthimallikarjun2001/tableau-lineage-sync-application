package com.example.tableau.entity;

import com.example.tableau.enums.StatusFlag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Tableau Worksheet (sheet within a workbook).
 */
@Entity
@Table(name = "tableau_worksheet",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId", "siteId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableauWorksheet {

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
     * Name of the worksheet
     */
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    /**
     * Owner username
     */
    @Column(name = "owner", length = 256)
    private String owner;

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
     * Parent workbook
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workbook_fk_id")
    @JsonIgnore
    private TableauWorkbook workbook;

    /**
     * List of report attributes (fields) in this worksheet
     */
    @OneToMany(mappedBy = "worksheet", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    @JsonIgnore
    private List<ReportAttribute> reportAttributes = new ArrayList<>();

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
