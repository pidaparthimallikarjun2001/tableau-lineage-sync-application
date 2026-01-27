package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO representing the type of a Collibra asset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraType {
    
    /**
     * Name of the asset type (e.g., "Tableau Server", "Tableau Workbook").
     */
    private String name;
}
