package com.example.tableau.dto;

import lombok.*;

/**
 * DTO for site switch response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSwitchResponse {

    private boolean success;
    private String siteId;
    private String siteName;
    private String siteContentUrl;
    private String authToken;
    private String message;
}
