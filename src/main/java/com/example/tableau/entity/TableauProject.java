package com.example.tableau.entity;

import com.example.tableau.enums.StatusFlag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Tableau Project.
 */
@Entity
@Table(name = "tableau_project",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId", "siteId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableauProject {

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
     * Name of the project
     */
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    /**
     * Description of the project
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Parent project ID (for nested projects)
     */
    @Column(name = "parent_project_id", length = 128)
    private String parentProjectId;

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
     * Parent site
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_fk_id")
    @JsonIgnore
    private TableauSite site;

    /**
     * List of workbooks in this project
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    @JsonIgnore
    private List<TableauWorkbook> workbooks = new ArrayList<>();

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
