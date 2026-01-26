# Tableau Identifier Usage: ID vs LUID

This document describes which identifier (`id` or `luid`) is used for each Tableau asset type when querying from Tableau APIs and storing in the database.

## Overview

Tableau provides two types of identifiers for most assets:
- **`id`**: A numeric identifier that is **always present** in Tableau API responses
- **`luid`**: Locally Unique Identifier (UUID format) - the primary identifier for most assets

**Important**: While `luid` is preferred for uniqueness and consistency, **it can be null or missing** for some assets (especially nested objects like sheets within workbooks). The `id` field, however, is **always present** in API responses. This is why the application uses a fallback pattern: prefer `luid` when available, but fall back to `id` to ensure data integrity.

This application uses a smart extraction strategy that accounts for these API behavior differences.

---

## Database Storage

**All asset types** store the identifier (LUID or ID) in the database:

| Asset Type | Database Column | Storage Value | Entity Field |
|------------|-----------------|---------------|--------------|
| Server | `asset_id` | **LUID** | `assetId` (String) |
| Site | `asset_id` | **ID** (from REST API) | `assetId` (String) |
| Project | `asset_id` | **ID** (from GraphQL) | `assetId` (String) |
| Workbook | `asset_id` | **LUID** or **ID** (fallback) | `assetId` (String) |
| Worksheet | `asset_id` | **LUID** or **ID** (fallback) | `assetId` (String) |
| DataSource | `asset_id` | **LUID** or **ID** (fallback) | `assetId` (String) |
| ReportAttribute | `asset_id` | **ID** (only available) | `assetId` (String) |

**Note**: The `assetId` field in all entity classes is documented as "Unique identifier from Tableau (LUID)" (see line 30-32 in each entity class), but in practice it stores whichever identifier is available and reliable from the API.

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

The application uses the `extractAssetId()` method in `BaseAssetService.java` (lines 105-114) which implements the following strategy:

```java
protected String extractAssetId(JsonNode node) {
    if (node == null || node.isMissingNode()) {
        return null;
    }
    String luid = node.path("luid").asText(null);
    if (luid != null && !luid.isEmpty()) {
        return luid;  // Prefer LUID
    }
    return node.path("id").asText(null);  // Fallback to ID
}
```

**Priority**: LUID > ID

### Why the Fallback Pattern is Critical

The fallback pattern `path("luid").asText(path("id").asText())` is used throughout the codebase because:

1. **API Behavior**: Tableau's GraphQL API returns both `id` and `luid` fields in queries, but:
   - **`id` is guaranteed** to be present in all responses
   - **`luid` can be null or missing** in certain scenarios:
     - Worksheets (sheets) nested within workbooks
     - Embedded data sources (not published)
     - Some child objects in the GraphQL hierarchy
     
2. **Data Integrity**: By using the fallback, the application ensures:
   - Every asset gets a valid identifier stored in the database
   - No records are skipped due to missing `luid`
   - The application remains robust across different Tableau API versions

3. **Preference for LUID**: When both are available:
   - `luid` is preferred because it's a UUID format that's more globally unique
   - `id` is used as a reliable fallback when `luid` is null

**Example Cases Where `luid` is Null**:
- Sheets queried as nested objects within workbooks
- Embedded (non-published) data sources
- Field instances in certain query contexts

However, individual services may have their own extraction patterns:

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
- **Database Storage**: `assetId` (LUID or ID)
- **REST API**: Uses REST client (delegates to GraphQL)
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` WORKBOOKS_QUERY lines 180-183
- **Extraction**: Prefers `luid`, falls back to `id`
  - Source: `WorkbookService.java` line 98
  - Code: `String assetId = worksheetNode.path("luid").asText(worksheetNode.path("id").asText());`
  - **Reason**: While workbooks typically have `luid`, the fallback to `id` ensures robustness if `luid` is null.

### 5. Worksheet
- **Database Storage**: `assetId` (LUID or ID)
- **REST API**: Uses REST client (delegates to GraphQL)
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` WORKSHEETS_QUERY lines 226-228
- **Extraction**: Prefers `luid`, falls back to `id`
  - Source: `WorksheetService.java` line 94
  - Code: `String assetId = worksheetNode.path("luid").asText(worksheetNode.path("id").asText());`
  - **Reason**: The `luid` field can be null for sheets in some contexts, but `id` is always present. The fallback ensures we always capture an identifier.
  - Code: `String assetId = worksheetNode.path("luid").asText(worksheetNode.path("id").asText());`

### 6. DataSource
- **Database Storage**: `assetId` (LUID or ID)
- **REST API**: Uses REST client (delegates to GraphQL)
- **GraphQL API**: Requests both `id` and `luid`
  - Source: `TableauGraphQLClient.java` PUBLISHED_DATASOURCES_QUERY lines 387-389
- **Extraction**: Prefers `luid`, falls back to `id`
  - Source: `DataSourceService.java` line 189
  - Code: `String assetId = dsNode.path("luid").asText(dsNode.path("id").asText());`
  - **Reason**: Data sources can be embedded or published. Embedded sources may not always have `luid`, so the fallback to `id` is critical.

### 7. ReportAttribute (Sheet Field Instance)
- **Database Storage**: `assetId` (ID or LUID depending on source)
- **REST API**: Not available via REST
- **GraphQL API**: Only `id` is available (no `luid` field for sheetFieldInstances)
  - Source: `TableauGraphQLClient.java` SHEET_FIELD_INSTANCES_QUERY line 235
- **Extraction**: Uses `id` field (only option available)
  - Source: `ReportAttributeService.java` line 107
  - Code: `String assetId = fieldNode.path("id").asText();`
  - **Note**: For related worksheets and datasources, uses `luid` with fallback to `id`
    - Line 113: `sheetNode.path("luid").asText(sheetNode.path("id").asText(null))`
    - Line 118: `dsNode.path("luid").asText(dsNode.path("id").asText(null))`

---

## Summary Table

| Asset Type | DB Storage | Tableau Query Source | Identifier Used | Extraction Pattern | Reason |
|------------|------------|---------------------|-----------------|-------------------|--------|
| **Server** | LUID | Manual/Config | N/A | Manually set | N/A |
| **Site** | ID | REST API | **id** | Direct `id` extraction | REST API returns `id` |
| **Project** | ID | GraphQL API | **id** | Direct `id` extraction (both queried) | Service uses `id` directly |
| **Workbook** | LUID or ID | GraphQL API | **luid** preferred | `luid` with `id` fallback | `luid` usually present, `id` as backup |
| **Worksheet** | LUID or ID | GraphQL API | **luid** preferred | `luid` with `id` fallback | **`luid` can be null, `id` always present** |
| **DataSource** | LUID or ID | GraphQL API | **luid** preferred | `luid` with `id` fallback | Embedded sources may lack `luid` |
| **ReportAttribute** | ID | GraphQL API | **id** | Only `id` available | API doesn't provide `luid` |

---

## Key Findings

1. **Consistent Storage**: All assets store their identifier in the `assetId` field in the database
2. **GraphQL Queries**: Most queries request both `id` and `luid` fields from Tableau's GraphQL API
3. **Why Fallback Pattern Exists**:
   - **`id` is always present** in Tableau API responses, guaranteed by the API
   - **`luid` can be null** for certain asset types, especially:
     - Nested objects (sheets within workbooks)
     - Embedded data sources
     - Some field instances
   - The fallback pattern `path("luid").asText(path("id").asText())` ensures data integrity by always having an identifier
4. **Extraction Strategy**:
   - **Sites and Projects**: Use `id` field directly (despite querying both)
   - **Workbooks, Worksheets, DataSources**: Prefer `luid`, fallback to `id` (handles null `luid` cases)
   - **ReportAttributes**: Use `id` only (GraphQL API doesn't provide `luid` for sheetFieldInstances)
5. **Base Service**: Provides `extractAssetId()` utility that prefers `luid` over `id`, but individual services may override this behavior

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
