# Tableau Asset ID Uniqueness Across Sites

## Executive Summary

**Question**: Are Tableau asset IDs unique across all sites?

**Answer**: **NO** - Tableau asset IDs are **NOT globally unique** across all sites. The same asset ID can exist in different sites for the same type of asset (e.g., a project, workbook, or worksheet).

## Understanding Tableau Asset ID Scoping

### What Are Tableau Asset IDs?

Tableau provides unique identifiers for assets like projects, workbooks, worksheets, and data sources. These identifiers come in two forms:
- **`id`**: A numeric or string identifier
- **`luid`**: Locally Unique Identifier (UUID format)

### Scope of Uniqueness

Tableau asset IDs are unique **within a site**, not **globally across all sites**. This means:

✅ **Within a single site**: Each asset has a unique ID  
❌ **Across multiple sites**: The same ID can appear in different sites for different assets

### Example Scenario

Consider two sites on the same Tableau Server:

**Site A (Sales Team)**
- Project ID: `abc-123-def`
- Workbook ID: `xyz-789-pqr`

**Site B (Marketing Team)**  
- Project ID: `abc-123-def` ← **Same ID as Site A's project**
- Workbook ID: `mno-456-stu`

Both projects have ID `abc-123-def`, but they are completely different projects in different sites.

## How This Application Handles ID Uniqueness

### Composite Unique Constraints

This application correctly handles multi-site scenarios by using **composite unique constraints** that combine `assetId` with `siteId`:

| Entity | Unique Constraint | Why? |
|--------|------------------|------|
| **TableauProject** | `(assetId, siteId)` | Same project ID can exist in different sites |
| **TableauWorkbook** | `(assetId, siteId)` | Same workbook ID can exist in different sites |
| **TableauWorksheet** | `(assetId, siteId)` | Same worksheet ID can exist in different sites |
| **TableauDataSource** | `(assetId, siteId)` | Same data source ID can exist in different sites |
| **ReportAttribute** | `(assetId, worksheetId, siteId)` | Field instances need worksheet context too |
| **TableauSite** | `(assetId)` | Sites themselves are globally unique |
| **TableauServer** | `(assetId)` | Servers are at the top level, globally unique |

### Entity Examples

#### TableauWorkbook Entity
```java
@Entity
@Table(name = "tableau_workbook",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId", "siteId"}))
public class TableauWorkbook {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Database primary key
    
    @Column(name = "asset_id", nullable = false, length = 128)
    private String assetId; // Tableau's asset ID
    
    @Column(name = "site_id", length = 128)
    private String siteId; // Site context for uniqueness
    
    // ... other fields
}
```

#### TableauSite Entity
```java
@Entity
@Table(name = "tableau_site",
    uniqueConstraints = @UniqueConstraint(columnNames = {"assetId"}))
public class TableauSite {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Database primary key
    
    @Column(name = "asset_id", nullable = false, length = 128)
    private String assetId; // Tableau's asset ID - globally unique for sites
    
    // Note: No siteId needed since sites are globally unique
}
```

## Best Practices for Multi-Site Scenarios

### 1. Always Include Site Context

When querying or referencing assets, always include the site context:

```java
// ❌ WRONG - Not unique across sites
Optional<TableauWorkbook> findByAssetId(String assetId);

// ✅ CORRECT - Unique when combined with site
Optional<TableauWorkbook> findByAssetIdAndSiteId(String assetId, String siteId);
```

### 2. Repository Methods

The application's repositories correctly implement site-aware queries:

```java
public interface TableauWorkbookRepository extends JpaRepository<TableauWorkbook, Long> {
    
    // Find workbook by asset ID and site ID
    Optional<TableauWorkbook> findByAssetIdAndSiteId(String assetId, String siteId);
    
    // Get all active workbooks for a specific site
    List<TableauWorkbook> findAllBySiteIdAndStatusFlag(String siteId, StatusFlag statusFlag);
}
```

### 3. Site Switching

The application maintains site context through `TableauAuthService`:

```java
@Service
public class TableauAuthService {
    
    private final AtomicReference<String> currentSiteId = new AtomicReference<>();
    private final AtomicReference<String> currentSiteName = new AtomicReference<>();
    
    // Switch site context
    public SiteSwitchResponse switchSite(String siteContentUrl) {
        // Updates current site context
        // All subsequent queries use this context
    }
}
```

### 4. Ingestion with Site Context

When ingesting data, the application always includes site context:

```java
public IngestionResult ingestWorkbooks() {
    String currentSiteId = tableauAuthService.getCurrentSiteId();
    
    // Fetch workbooks from current site
    JsonNode workbooks = tableauGraphQLClient.fetchWorkbooks();
    
    for (JsonNode workbookNode : workbooks) {
        String assetId = workbookNode.path("id").asText();
        
        // Find existing workbook by BOTH assetId and siteId
        Optional<TableauWorkbook> existing = 
            workbookRepository.findByAssetIdAndSiteId(assetId, currentSiteId);
        
        // ... process with site context
    }
}
```

## Why This Matters

### Data Integrity

Without proper site scoping:
- ❌ Data from different sites could collide
- ❌ Updates to Site A could affect Site B
- ❌ Deletions in Site A could delete data from Site B

With composite unique constraints:
- ✅ Each site's data is isolated
- ✅ Same asset ID in different sites creates separate records
- ✅ Safe to synchronize multiple sites in the same database

### Example Scenario

**Without site scoping** (incorrect):
```
Database has one record with assetId='abc-123'
1. Sync Site A - finds existing record, updates it
2. Sync Site B - finds same record, overwrites Site A's data ❌
```

**With site scoping** (correct):
```
Database has separate records for each site
1. Sync Site A - finds/creates record with (assetId='abc-123', siteId='site-a')
2. Sync Site B - finds/creates record with (assetId='abc-123', siteId='site-b') ✅
```

## API Endpoint Considerations

### Database ID vs Asset ID

The application provides two ways to query assets:

1. **By Database Primary Key** (globally unique):
   ```
   GET /api/workbooks/1
   ```
   Uses the auto-generated database ID (1, 2, 3, etc.)

2. **By Asset ID** (requires site context):
   ```
   GET /api/sites/asset/{assetId}
   ```
   For sites, this works because site asset IDs are globally unique.
   
   For other assets, you would need to include site context:
   ```
   GET /api/workbooks?assetId=abc-123&siteId=site-a
   ```

### Current Implementation

The current API endpoints use database primary keys for individual resource access, which automatically ensures uniqueness:

```java
// Uses database PK - always unique
@GetMapping("/{id}")
public TableauWorkbook getWorkbookById(@PathVariable Long id) {
    return workbookRepository.findById(id).orElseThrow();
}

// Listing operations are site-aware
@GetMapping
public List<TableauWorkbook> getActiveWorkbooks() {
    String siteId = tableauAuthService.getCurrentSiteId();
    return workbookRepository.findAllBySiteIdAndStatusFlag(siteId, StatusFlag.ACTIVE);
}
```

## Summary

### Key Takeaways

1. **Tableau asset IDs are NOT globally unique** - they are unique only within a site
2. **This application correctly handles this** by using composite unique constraints
3. **Always include site context** when querying or referencing assets (except for sites themselves)
4. **The composite key pattern** `(assetId, siteId)` ensures data integrity across multiple sites
5. **Repository methods** are site-aware to prevent cross-site data conflicts

### When Asset IDs Are Globally Unique

Only these entities have globally unique asset IDs:
- **TableauServer** - Server is the top-level container
- **TableauSite** - Sites are unique across a server

### When Asset IDs Require Site Context

All other entities require site context for uniqueness:
- **TableauProject**
- **TableauWorkbook**
- **TableauWorksheet**
- **TableauDataSource**
- **ReportAttribute**

## Related Documentation

- **[IDENTIFIER_USAGE.md](IDENTIFIER_USAGE.md)** - Explains which identifiers (id vs luid) are used
- **[TABLE_SCHEMA_REFERENCE.md](TABLE_SCHEMA_REFERENCE.md)** - Database schema with unique constraints
- **[README.md](README.md)** - Application overview and entity relationships

---

*Last Updated: 2026-01-27*
