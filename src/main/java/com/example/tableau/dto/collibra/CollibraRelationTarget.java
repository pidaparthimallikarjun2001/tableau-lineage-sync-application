package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO representing a relation target in Collibra.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraRelationTarget {
    
    /**
     * Domain where the target asset exists.
     */
    private CollibraDomain domain;
    
    /**
     * Name of the target asset.
     */
    private String name;
}
