package com.example.tableau.entity;

import com.example.tableau.enums.StatusFlag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Tableau Server instance.
 */
@Entity
@Table(name = "tableau_server",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableauServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier from Tableau (LUID)
     */
    @Column(name = "asset_id", nullable = false, length = 128)
    private String assetId;

    /**
     * Name of the Tableau Server
     */
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    /**
     * URL of the Tableau Server
     */
    @Column(name = "server_url", length = 1024)
    private String serverUrl;

    /**
     * Tableau Server version
     */
    @Column(name = "version", length = 64)
    private String version;

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
     * List of sites belonging to this server
     */
    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    @JsonIgnore
    private List<TableauSite> sites = new ArrayList<>();

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
