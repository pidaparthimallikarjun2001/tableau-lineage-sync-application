package com.example.tableau.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for OpenAPI/Swagger documentation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tableau Lineage Sync API")
                        .version("1.0.0")
                        .description("REST API for synchronizing Tableau metadata and lineage information. " +
                                "This API provides endpoints to fetch Tableau assets (Server, Site, Project, " +
                                "Workbook, Worksheet, Report Attributes, Data Sources) and ingest them into a database " +
                                "with full change tracking support (INSERT, UPDATE, DELETE).\n\n" +
                                "**Key Features:**\n" +
                                "- Fetch metadata directly from Tableau's GraphQL and REST APIs\n" +
                                "- Ingest data into local database (H2 for dev, MariaDB for production)\n" +
                                "- Incremental updates with change tracking\n" +
                                "- Cascading soft deletion for hierarchical assets\n" +
                                "- Dynamic Tableau site switching\n" +
                                "- Complete lineage information extraction")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server")
                ));
    }
}
