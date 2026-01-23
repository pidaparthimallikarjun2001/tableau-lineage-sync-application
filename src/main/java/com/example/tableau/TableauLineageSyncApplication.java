package com.example.tableau;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application class for Tableau Lineage Sync.
 * 
 * This application integrates with Tableau's REST and GraphQL APIs to extract
 * metadata and lineage information for various Tableau assets including:
 * - Server, Site, Project, Workbook, Worksheet
 * - Report Attributes (Sheet Field Instances)
 * - Data Sources (Direct and Custom SQL)
 * 
 * The application supports:
 * - Initial full data ingestion
 * - Incremental updates with change tracking (INSERT, UPDATE, DELETE)
 * - Dynamic site switching
 * - H2 (local) and MariaDB (production) databases
 */
@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Tableau Lineage Sync API",
        version = "1.0.0",
        description = "REST API for synchronizing Tableau metadata and lineage information. " +
            "This API provides endpoints to fetch Tableau assets and ingest them into a database " +
            "with full change tracking support (INSERT, UPDATE, DELETE).",
        contact = @Contact(
            name = "API Support",
            email = "support@example.com"
        ),
        license = @License(
            name = "Apache 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        )
    )
)
public class TableauLineageSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(TableauLineageSyncApplication.class, args);
    }
}
