# Tableau Lineage Sync Application

A comprehensive Java Spring Boot application that integrates with Tableau's REST and GraphQL (Metadata) APIs to extract, store, and track changes in Tableau metadata and lineage information.

## 📌 Quick Start: Seeing the UPDATED Status

**New to this app and want to see the UPDATED status?** See **[QUICK_ANSWER.md](QUICK_ANSWER.md)** for a 30-second test.

**Key Points:**
- ✅ Works for: Projects, Workbooks, Published Data Sources
- ❌ Doesn't work for: Worksheets (they show DELETE + NEW due to Tableau's ID behavior)
- See **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)** for complete details

## ⚠️ Important: Asset ID Uniqueness

**Are Tableau asset IDs unique across all sites?** **NO** - Tableau asset IDs are **NOT globally unique**. The same asset ID can exist in different sites.

This application correctly handles multi-site scenarios using composite unique constraints `(assetId, siteId)`. See **[ASSET_ID_UNIQUENESS.md](ASSET_ID_UNIQUENESS.md)** for complete details on:
- Why IDs are not globally unique
- How this application ensures data integrity
- Best practices for multi-site deployments

## Features

### Core Functionality

1. **Initial Data Ingestion**
   - Creates database tables for each Tableau asset type
   - Ingests all Tableau metadata and lineage information on first run

2. **Incremental Updates and Change Tracking**
   - Compares current Tableau response with existing database data
   - Updates status flags to track changes:
     - `NEW`: Records found in Tableau not in database
     - `UPDATED`: Existing records with metadata changes
     - `DELETED`: Records in database no longer in Tableau (soft delete)
     - `ACTIVE`: Records synchronized with no changes

3. **Cascading Soft Deletion**
   - When a parent asset is marked as DELETED, all child assets are recursively soft-deleted
   - Hierarchy: Server → Site → Project → Workbook → Worksheet → Report Attributes
   - Data Sources are linked to Workbooks for embedded sources

4. **REST API with Swagger UI**
   - Comprehensive API endpoints for each asset type
   - Interactive Swagger documentation at `/swagger-ui.html`
   - Two primary operations per asset type:
     - **Fetch**: Retrieve data from Tableau (does not persist)
     - **Ingest**: Fetch and persist with change tracking

### Supported Tableau Assets

| Asset Type | Description |
|------------|-------------|
| **Server** | Tableau Server instance details |
| **Site** | Tableau Sites within a server |
| **Project** | Projects within a site (supports nested projects) |
| **Workbook** | Workbooks with owner and metadata |
| **Worksheet** | Sheets within workbooks |
| **Report Attribute** | Sheet field instances with lineage and formulas |
| **Data Source** | Published, embedded, and custom SQL data sources |

### Data Source Types

- **DIRECT_IMPORT**: Direct database table connections
- **CUSTOM_SQL**: Custom SQL queries defining data sources
- **PUBLISHED**: Published/shared data sources
- **FILE_BASED**: File-based sources (Excel, CSV, etc.)

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Data JPA** with Hibernate
- **H2 Database** (file-based persistence for local development)
- **MariaDB** (production)
- **WebFlux WebClient** for async HTTP
- **Resilience4j** for circuit breaker/retry
- **springdoc-openapi** for Swagger UI
- **Lombok** for boilerplate reduction

## Project Structure

```
src/main/java/com/example/tableau/
├── TableauLineageSyncApplication.java  # Main application class
├── config/                              # Configuration classes
│   ├── TableauApiConfig.java           # Tableau API settings
│   ├── WebClientConfig.java            # HTTP client config
│   └── OpenApiConfig.java              # Swagger config
├── controller/                          # REST controllers
│   ├── TableauSiteController.java      # Auth & site switching
│   ├── ServerController.java
│   ├── SiteController.java
│   ├── ProjectController.java
│   ├── WorkbookController.java
│   ├── WorksheetController.java
│   ├── ReportAttributeController.java
│   └── DataSourceController.java
├── dto/                                 # Data transfer objects
│   ├── IngestionResult.java
│   ├── SiteSwitchRequest.java
│   ├── SiteSwitchResponse.java
│   └── AssetDto.java
├── entity/                              # JPA entities
│   ├── TableauServer.java
│   ├── TableauSite.java
│   ├── TableauProject.java
│   ├── TableauWorkbook.java
│   ├── TableauWorksheet.java
│   ├── ReportAttribute.java
│   └── TableauDataSource.java
├── enums/                               # Enumerations
│   ├── StatusFlag.java                 # NEW, UPDATED, DELETED, ACTIVE
│   └── SourceType.java                 # DIRECT_IMPORT, CUSTOM_SQL, etc.
├── exception/                           # Exception handling
│   ├── TableauApiException.java
│   ├── DataIngestionException.java
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java
├── repository/                          # JPA repositories
│   ├── TableauServerRepository.java
│   ├── TableauSiteRepository.java
│   ├── TableauProjectRepository.java
│   ├── TableauWorkbookRepository.java
│   ├── TableauWorksheetRepository.java
│   ├── ReportAttributeRepository.java
│   └── TableauDataSourceRepository.java
└── service/                             # Business logic
    ├── BaseAssetService.java           # Common service methods
    ├── TableauAuthService.java         # Authentication & site switching
    ├── TableauGraphQLClient.java       # GraphQL API client
    ├── TableauRestClient.java          # REST API client
    ├── ServerService.java
    ├── SiteService.java
    ├── ProjectService.java
    ├── WorkbookService.java
    ├── WorksheetService.java
    ├── ReportAttributeService.java
    └── DataSourceService.java
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Access to a Tableau Server with Metadata API enabled

### Configuration

1. Copy and configure `application.properties`:

```properties
# Tableau Server Configuration
tableau.base-url=https://your-tableau-server.com
tableau.api-version=3.17
tableau.auth-mode=PAT

# Personal Access Token (recommended)
tableau.pat.name=your-token-name
tableau.pat.secret=your-token-secret

# OR Username/Password authentication
# tableau.auth-mode=BASIC
# tableau.username=your-username
# tableau.password=your-password

# Default site (leave empty for Default site)
tableau.default-site-id=
```

### Build and Run

```bash
# Build the application
mvn clean install

# Run with H2 database (default)
mvn spring-boot:run

# Run with MariaDB
mvn spring-boot:run -Dspring.profiles.active=mariadb
```

Or run the JAR directly:

```bash
# Build JAR
mvn clean package

# Run with default (H2) profile
java -jar target/tableau-lineage-sync-application-0.0.1-SNAPSHOT.jar

# Run with MariaDB profile
java -jar target/tableau-lineage-sync-application-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

### MariaDB Setup

For MariaDB, set environment variables:

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=tableaudb
export DB_USER=your-user
export DB_PASS=your-password
```

## API Endpoints

### Understanding IDs: Database ID vs Asset ID

This application stores Tableau metadata in a local database. Each entity has two types of IDs:

1. **Database ID (Primary Key)**: Auto-generated sequential number (1, 2, 3, etc.) used as the primary key in the local database
2. **Asset ID (LUID)**: Tableau's unique identifier (UUID format like `a1b2c3d4-e5f6-7890-abcd-ef1234567890`)

**When to use which ID:**
- Use **database ID** for endpoints like `GET /api/sites/{id}` or `DELETE /api/sites/{id}`
- Use **asset ID** for endpoints like `GET /api/sites/asset/{assetId}` when you have the Tableau LUID

**Example:**
- Database ID endpoint: `GET /api/sites/1` (retrieves site with database primary key = 1)
- Asset ID endpoint: `GET /api/sites/asset/a1b2c3d4-e5f6-7890-abcd-ef1234567890` (retrieves site by Tableau LUID)

**For detailed information** about which identifiers (id vs luid) are used when querying from Tableau and storing in the database for each asset type, see [IDENTIFIER_USAGE.md](IDENTIFIER_USAGE.md).

### Authentication & Site Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tableau/site/current` | Get current site info |
| POST | `/api/tableau/site/switch` | Switch to different site |
| POST | `/api/tableau/auth/signin` | Sign in to Tableau |
| POST | `/api/tableau/auth/signout` | Sign out |

### Asset Endpoints

Each asset type (servers, sites, projects, workbooks, worksheets, report-attributes, datasources) has:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/{asset}` | Get all active assets |
| GET | `/api/{asset}/{id}` | Get asset by database ID (primary key) |
| GET | `/api/{asset}/fetch` | Fetch from Tableau (no persist) |
| POST | `/api/{asset}/ingest` | Fetch and persist with change tracking |
| DELETE | `/api/{asset}/{id}` | Soft delete asset and children |

**Additional Site-Specific Endpoint:**
- `GET /api/sites/asset/{assetId}` - Get site by Tableau asset ID (LUID)

### Swagger UI

Access the interactive API documentation at:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

## Ingestion Workflow

1. **Authenticate to Tableau**
   ```
   POST /api/tableau/auth/signin
   ```

2. **Switch site if needed**
   ```
   POST /api/tableau/site/switch
   Body: { "siteContentUrl": "YourSiteName" }
   ```

3. **Ingest assets in order** (respects hierarchy):
   ```
   POST /api/servers/ingest
   POST /api/sites/ingest
   POST /api/projects/ingest
   POST /api/workbooks/ingest
   POST /api/worksheets/ingest
   POST /api/datasources/ingest
   POST /api/report-attributes/ingest
   ```

## Change Detection

The application uses metadata hashing for efficient change detection:

1. **Hash Generation**: SHA-256 hash of relevant fields
2. **Comparison**: Existing hash vs new hash
3. **Status Update**:
   - No existing record → `NEW`
   - Hash different → `UPDATED`
   - Hash same → `ACTIVE`
   - Not in Tableau → `DELETED`

**Important:** Only specific metadata fields are tracked for each asset type. For detailed information on which fields trigger the `UPDATED` status and examples of changes to make in Tableau, see **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)**.

## GraphQL Queries

The application uses Tableau's Metadata API (GraphQL) for detailed metadata including:

- Projects with hierarchy
- Workbooks with sheets and data sources
- Sheet field instances with lineage
- Upstream fields, tables, and columns
- Calculated field formulas
- Custom SQL queries

**Important**: Tableau's GraphQL API operates at the site level. Use the site switch endpoint to change context.

## Entity Relationships

```
TableauServer (1) ──────────────── (Many) TableauSite
                                           │
TableauSite (1) ────────────────── (Many) TableauProject
                                           │
TableauProject (1) ─────────────── (Many) TableauWorkbook
                                           │
TableauWorkbook (1) ───────────── (Many) TableauWorksheet
        │                                  │
        │                          TableauWorksheet (1) ── (Many) ReportAttribute
        │                                                          │
        └──────── (Many) TableauDataSource ◄───────────────────────┘
```

## H2 Console (Development)

Access the H2 database console at: `http://localhost:8080/h2-console`

**Connection Settings:**
- **JDBC URL**: `jdbc:h2:file:./data/tableaudb`
- **Username**: `sa`
- **Password**: (empty)

**Note**: The H2 database now uses file-based persistence. Database files are stored in the `./data/` directory, and data will persist across application restarts.

## Documentation

Comprehensive documentation is available for various aspects of the application:

| Document | Description |
|----------|-------------|
| **[ASSET_ID_UNIQUENESS.md](ASSET_ID_UNIQUENESS.md)** | **Are Tableau asset IDs unique across sites?** Comprehensive explanation of ID uniqueness scoping and multi-site data integrity |
| **[TABLE_SCHEMA_REFERENCE.md](TABLE_SCHEMA_REFERENCE.md)** | Detailed schema reference for `report_attribute` and `tableau_datasource` tables with column descriptions and SQL examples |
| **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)** | Which fields trigger change detection (UPDATED status) for each asset type |
| **[IDENTIFIER_USAGE.md](IDENTIFIER_USAGE.md)** | Explanation of ID vs LUID usage across different Tableau assets |
| **[UPDATED_STATUS_EXPLAINED.md](UPDATED_STATUS_EXPLAINED.md)** | How the change tracking and UPDATED status works |
| **[QUICK_ANSWER.md](QUICK_ANSWER.md)** | Quick 30-second test to see the UPDATED status in action |
| **[SEEING_UPDATED_STATUS.md](SEEING_UPDATED_STATUS.md)** | Step-by-step guide to testing change detection |

## Troubleshooting

### Common Issues

1. **Authentication Failed**
   - Verify PAT name and secret
   - Check if PAT has required permissions
   - Ensure Metadata API is enabled on server

2. **GraphQL Errors**
   - Confirm you're authenticated to a site
   - Verify site has Metadata API access
   - Check server's API version compatibility

3. **Connection Issues**
   - Verify Tableau server URL
   - Check network connectivity
   - Review firewall settings

### Logging

Enable debug logging in `application.properties`:

```properties
logging.level.com.example.tableau=DEBUG
logging.level.org.springframework.web.reactive.function.client=DEBUG
```

## License

Apache 2.0
