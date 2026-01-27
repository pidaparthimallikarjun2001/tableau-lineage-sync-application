package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO representing a Collibra community.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraCommunity {
    
    /**
     * Name of the community.
     */
    private String name;
}
