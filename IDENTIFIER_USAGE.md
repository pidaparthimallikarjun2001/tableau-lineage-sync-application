# Tableau Identifier Usage: ID vs LUID

This document describes which identifier (`id` or `luid`) is used for each Tableau asset type when querying from Tableau APIs and storing in the database.

## Overview

Tableau provides two types of identifiers for most assets:
- **`id`**: A numeric identifier that is **always present** in Tableau API responses
- **`luid`**: Locally Unique Identifier (UUID format) - used in some contexts

**Decision**: This application uses **`id` exclusively** for all asset types to:
1. **Eliminate confusion** - Always know which identifier is being used
2. **Ensure consistency** - All assets use the same identifier type
3. **Guarantee reliability** - `id` is always present (unlike `luid` which can be null)
4. **Simplify code** - No need for fallback logic or conditional extraction

> **⚠️ Important Note on ID Uniqueness**: Tableau asset IDs are **NOT globally unique across sites**. The same asset ID can exist in different sites. This application uses composite unique constraints `(assetId, siteId)` to ensure data integrity. See **[ASSET_ID_UNIQUENESS.md](ASSET_ID_UNIQUENESS.md)** for details.

---

## Database Storage

**All asset types** store the `id` in the database:

| Asset Type | Database Column | Storage Value | Entity Field |
|------------|-----------------|---------------|--------------|
| Server | `asset_id` | **LUID** | `assetId` (String) |
| Site | `asset_id` | **ID** | `assetId` (String) |
| Project | `asset_id` | **ID** | `assetId` (String) |
| Workbook | `asset_id` | **ID** | `assetId` (String) |
| Worksheet | `asset_id` | **ID** | `assetId` (String) |
| DataSource | `asset_id` | **ID** | `assetId` (String) |
| ReportAttribute | `asset_id` | **ID** | `assetId` (String) |

**Note**: The `assetId` field in all entity classes is documented as "Unique identifier from Tableau (LUID)" (see line 30-32 in each entity class), but in practice it consistently stores the `id` field for reliability and consistency.

---

## Querying from Tableau

### GraphQL API Queries

The application queries **both `id` and `luid`** fields from Tableau's GraphQL API and uses intelligent extraction logic.

#### GraphQL Query Fields

**Sites Query** (`SITES_QUERY` - line 128-137):
```graphql
tableauSites {
    id
    name
    uri
    luid
}
```

**Projects Query** (`PROJECTS_QUERY` - line 142-168):
```graphql
nodes {
    id
    name
    luid
    description
    ...
}
```

**Workbooks Query** (`WORKBOOKS_QUERY` - line 173-213):
```graphql
nodes {
    id
    name
    luid
    description
    ...
}
```

**Worksheets Query** (`WORKSHEETS_QUERY` - line 218-263):
```graphql
nodes {
    id
    name
    luid
    workbook {
        id
        name
        luid
    }
    ...
}
```

**Data Sources Query** (`PUBLISHED_DATASOURCES_QUERY` - line 376-431):
```graphql
nodes {
    id
    name
    luid
    description
    ...
}
```

**Sheet Field Instances Query** (`SHEET_FIELD_INSTANCES_QUERY` - line 340-371):
```graphql
nodes {
    id    # NOTE: Only 'id' is available for sheetFieldInstances
    name
    ...
    datasource {
        id
        name
        luid
    }
}
```

---

### Extraction Logic

**Simple and Consistent**: All services use direct `id` extraction for reliability and consistency:

```java
String assetId = node.path("id").asText();
```

**Why This Works**:
- **`id` is always present** in Tableau API responses
- **No conditional logic needed** - straightforward extraction
- **Consistent across all asset types** - eliminates confusion
- **No null handling required** - `id` is guaranteed to exist

The `extractAssetId()` method in `BaseAssetService.java` is no longer used to avoid the complexity and confusion of fallback patterns.

---

## Asset-Specific Identifier Usage

### 1. Server
- **Database Storage**: `assetId` (LUID)
- **REST API**: Not queried via REST API
- **GraphQL API**: Not available in GraphQL
- **Extraction**: Manually configured/created

### 2. Site
- **Database Storage**: `assetId` (LUID)
- **REST API**: Uses `id` field
  - Source: `SiteService.java` line 90
  - Code: `String assetId = siteNode.path("id").asText();`
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` SITES_QUERY lines 130-134
- **Extraction**: Uses `id` from REST API response

### 3. Project
- **Database Storage**: `assetId` (LUID)
- **REST API**: Uses `id` field
  - Source: `ProjectService.java` line 96
  - Code: `String assetId = projectNode.path("id").asText();`
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` PROJECTS_QUERY lines 150-152
- **Extraction**: Uses `id` from GraphQL API response
  - **Note**: Although both fields are queried, the service explicitly uses `id` field

### 4. Workbook
- **Database Storage**: `assetId` (ID)
- **REST API**: Uses REST client (delegates to GraphQL)
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` WORKBOOKS_QUERY lines 180-183
- **Extraction**: Uses `id` directly (no fallback)
  - Source: `WorkbookService.java` line 98
  - Code: `String assetId = workbookNode.path("id").asText();`
  - **Reason**: Using `id` only ensures consistency across all asset types and eliminates confusion about which identifier is stored.

### 5. Worksheet
- **Database Storage**: `assetId` (ID)
- **REST API**: Uses REST client (delegates to GraphQL)
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` WORKSHEETS_QUERY lines 226-228
- **Extraction**: Uses `id` directly (no fallback)
  - Source: `WorksheetService.java` line 94
  - Code: `String assetId = worksheetNode.path("id").asText();`
  - **Reason**: The `luid` field is frequently null for worksheets (sheets are nested objects within workbooks). Using `id` directly ensures data integrity and avoids empty `assetId` values in the database.

### 6. DataSource
- **Database Storage**: `assetId` (ID)
- **REST API**: Uses REST client (delegates to GraphQL)
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` PUBLISHED_DATASOURCES_QUERY lines 387-389
- **Extraction**: Uses `id` directly (no fallback)
  - Source: `DataSourceService.java` line 189
  - Code: `String assetId = dsNode.path("id").asText();`
  - **Reason**: Using `id` only ensures consistency across all asset types and eliminates confusion about which identifier is stored.

### 7. ReportAttribute (Sheet Field Instance)
- **Database Storage**: `assetId` (ID)
- **REST API**: Not available via REST
- **GraphQL API**: Only `id` is available (no `luid` field for sheetFieldInstances)
  - Source: `TableauGraphQLClient.java` SHEET_FIELD_INSTANCES_QUERY line 235
- **Extraction**: Uses `id` field (only option available)
  - Source: `ReportAttributeService.java` line 107
  - Code: `String assetId = fieldNode.path("id").asText();`
  - **Note**: For related worksheets and datasources, also uses `id` only for consistency
    - Line 113: `sheetNode.path("id").asText(null)`
    - Line 118: `dsNode.path("id").asText(null)`

---

## Summary Table

| Asset Type | DB Storage | Tableau Query Source | Identifier Used | Extraction Pattern | Reason |
|------------|------------|---------------------|-----------------|-------------------|--------|
| **Server** | LUID | Manual/Config | N/A | Manually set | N/A |
| **Site** | ID | REST API | **id** | Direct `id` extraction | REST API returns `id` |
| **Project** | ID | GraphQL API | **id** | Direct `id` extraction (both queried) | Service uses `id` directly |
| **Workbook** | ID | GraphQL API | **id** | Direct `id` extraction | Consistent with all assets, eliminates confusion |
| **Worksheet** | ID | GraphQL API | **id** | Direct `id` extraction | **`luid` is frequently null for sheets, `id` always present** |
| **DataSource** | ID | GraphQL API | **id** | Direct `id` extraction | Consistent with all assets, eliminates confusion |
| **ReportAttribute** | ID | GraphQL API | **id** | Only `id` available | API doesn't provide `luid` |

---

## Key Findings

1. **Consistent Approach**: All assets use **`id` only** for simplicity and reliability
2. **GraphQL Queries**: Most queries request both `id` and `luid` fields, but the application exclusively uses `id`
3. **Why Use ID Only**:
   - **`id` is always present** in Tableau API responses, guaranteed by the API
   - **`luid` can be null** for certain asset types (sheets, embedded sources, field instances)
   - **Eliminates confusion** - Always know which identifier is stored
   - **Simplifies code** - No need for fallback logic or conditional extraction
   - **Ensures consistency** - All assets use the same identifier type
4. **Extraction Strategy**: All services use direct `id` extraction: `node.path("id").asText()`
5. **No Fallback Pattern**: The previous `extractAssetId()` utility method is no longer used to avoid confusion

---

## Related Files

### Entity Classes
- `/src/main/java/com/example/tableau/entity/TableauServer.java`
- `/src/main/java/com/example/tableau/entity/TableauSite.java`
- `/src/main/java/com/example/tableau/entity/TableauProject.java`
- `/src/main/java/com/example/tableau/entity/TableauWorkbook.java`
- `/src/main/java/com/example/tableau/entity/TableauWorksheet.java`
- `/src/main/java/com/example/tableau/entity/TableauDataSource.java`
- `/src/main/java/com/example/tableau/entity/ReportAttribute.java`

### Service Classes
- `/src/main/java/com/example/tableau/service/BaseAssetService.java` (lines 105-114)
- `/src/main/java/com/example/tableau/service/SiteService.java` (line 90)
- `/src/main/java/com/example/tableau/service/ProjectService.java` (line 96)
- `/src/main/java/com/example/tableau/service/WorkbookService.java` (line 98)
- `/src/main/java/com/example/tableau/service/WorksheetService.java` (line 98)
- `/src/main/java/com/example/tableau/service/DataSourceService.java` (line 189)
- `/src/main/java/com/example/tableau/service/ReportAttributeService.java` (line 107)

### GraphQL Client
- `/src/main/java/com/example/tableau/service/TableauGraphQLClient.java` (lines 128-562)

---

## Recommendations

1. **Consistency**: Consider standardizing on `luid` across all asset types for consistency
2. **Documentation**: The entity field comments correctly state "LUID", but some services use `id`
3. **Migration**: If changing from `id` to `luid` for Sites/Projects, ensure data migration for existing records
4. **ReportAttributes**: Limited by Tableau's GraphQL API which doesn't provide `luid` for sheetFieldInstances

---

*Last Updated: 2026-01-26*
