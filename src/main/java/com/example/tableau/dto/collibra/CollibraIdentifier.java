package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO representing the identifier of a Collibra asset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraIdentifier {
    
    /**
     * Unique identifier name (e.g., "Server id > Servername").
     */
    private String name;
    
    /**
     * Domain where the asset exists.
     */
    private CollibraDomain domain;
}
