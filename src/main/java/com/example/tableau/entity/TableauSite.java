package com.example.tableau.entity;

import com.example.tableau.enums.CollibraSyncStatus;
import com.example.tableau.enums.StatusFlag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Tableau Site.
 */
@Entity
@Table(name = "tableau_site",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableauSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier from Tableau (LUID)
     */
    @Column(name = "asset_id", nullable = false, length = 128)
    private String assetId;

    /**
     * Name of the site
     */
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    /**
     * Content URL of the site (used for switching sites)
     */
    @Column(name = "content_url", length = 512)
    private String contentUrl;

    /**
     * URL of the site
     */
    @Column(name = "site_url", length = 1024)
    private String siteUrl;

    /**
     * Status flag for change tracking
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_flag", nullable = false, length = 20)
    @Builder.Default
    private StatusFlag statusFlag = StatusFlag.NEW;

    /**
     * Status flag for tracking synchronization with Collibra.
     * Indicates whether this asset has been imported to Collibra and
     * tracks if there are pending changes that need to be synchronized.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "collibra_sync_status", nullable = false, length = 20)
    @Builder.Default
    private CollibraSyncStatus collibraSyncStatus = CollibraSyncStatus.NOT_SYNCED;

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
     * Parent server
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    @JsonIgnore
    private TableauServer server;

    /**
     * List of projects belonging to this site
     */
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    @JsonIgnore
    private List<TableauProject> projects = new ArrayList<>();

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
