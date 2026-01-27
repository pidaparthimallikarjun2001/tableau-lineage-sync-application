package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO representing a Collibra domain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraDomain {
    
    /**
     * Name of the domain.
     */
    private String name;
    
    /**
     * Community where the domain exists.
     */
    private CollibraCommunity community;
}
