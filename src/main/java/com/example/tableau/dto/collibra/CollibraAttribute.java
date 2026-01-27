package com.example.tableau.dto.collibra;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * DTO representing an attribute for a Collibra asset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollibraAttribute {

    /**
     * Name of the attribute type in Collibra.
     */
    private String attributeTypeName;

    /**
     * Value of the attribute.
     */
    private String value;
}
