package com.example.tableau.dto;

import lombok.*;

/**
 * DTO for site switch request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSwitchRequest {

    /**
     * The content URL of the site to switch to.
     * This is the site's URL name (e.g., "MySite" from "https://server/t/MySite")
     */
    private String siteContentUrl;
}
