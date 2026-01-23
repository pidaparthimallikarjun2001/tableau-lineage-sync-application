package com.example.tableau.dto;

import com.example.tableau.enums.StatusFlag;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Generic DTO for asset information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDto {

    private Long id;
    private String assetId;
    private String name;
    private String description;
    private String type;
    private StatusFlag statusFlag;
    private LocalDateTime createdTimestamp;
    private LocalDateTime lastUpdatedTimestamp;
    private String metadataHash;
    
    // Additional fields for different asset types
    private String parentId;
    private String parentName;
    private String siteId;
    private String owner;
}
