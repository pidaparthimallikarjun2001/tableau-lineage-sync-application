# Foreign Key Design Rationale

## Overview

This document explains the design decisions around foreign keys in the Tableau Lineage Sync Application, specifically addressing common questions about why we use numeric database IDs instead of Tableau asset IDs.

## Questions Addressed

### Q1: Is `datasource_fk_id` considered while generating metadata hash for report_attribute table?

**Answer: No**, and this is intentional.

**Reasoning:**
- The metadata hash uses **Tableau asset IDs** (e.g., `source_datasource_id`, `worksheetId`) to detect meaningful metadata changes
- Database foreign key IDs (`datasource_fk_id`, `worksheet_fk_id`) are **implementation details** for database relationships
- Foreign keys can change due to database operations (e.g., recreating records) without representing actual Tableau metadata changes

**What IS included in the metadata hash for report_attribute:**
- Asset ID (Tableau LUID)
- Name (field name)
- Worksheet ID (Tableau asset ID, not database FK)
- Data Source ID (Tableau asset ID, not database FK)
- Field Role (dimension, measure, etc.)
- Is Calculated (true/false)
- Calculation Logic/Formula
- Lineage Information
- Site ID

**Important Fix:** While FKs are not in the hash, they ARE updated during every ingestion to maintain referential integrity, even when the metadata hash hasn't changed.

---

### Q2: Why use numeric serial IDs as foreign keys instead of Tableau asset IDs?

**Answer:** Numeric foreign keys provide better performance and follow database best practices.

## Database Design Pattern

### Current Design (Recommended)

```
┌─────────────────────────────┐
│   TableauDataSource         │
├─────────────────────────────┤
│ id (BIGINT) ← PK            │  ← Auto-increment numeric ID
│ asset_id (VARCHAR) ← UK     │  ← Tableau LUID (unique with site_id)
│ site_id (VARCHAR)           │
│ name (VARCHAR)              │
└─────────────────────────────┘
           ▲
           │ FK: datasource_fk_id (BIGINT)
           │
┌─────────────────────────────┐
│   ReportAttribute           │
├─────────────────────────────┤
│ id (BIGINT) ← PK            │
│ asset_id (VARCHAR) ← UK     │
│ datasource_fk_id (BIGINT)   │  ← References TableauDataSource.id
│ source_datasource_id (VAR)  │  ← Stores Tableau asset ID (for hash)
└─────────────────────────────┘
```

### Alternative Design (Not Recommended)

```
┌─────────────────────────────┐
│   TableauDataSource         │
├─────────────────────────────┤
│ asset_id (VARCHAR) ← PK     │  ← Would need composite PK
│ site_id (VARCHAR) ← PK      │
│ name (VARCHAR)              │
└─────────────────────────────┘
           ▲
           │ FK: datasource_asset_id + site_id
           │
┌─────────────────────────────┐
│   ReportAttribute           │
├─────────────────────────────┤
│ asset_id (VARCHAR) ← PK     │
│ site_id (VARCHAR) ← PK      │
│ datasource_asset_id (VAR)   │  ← Would reference asset_id
│ datasource_site_id (VAR)    │  ← Would need site_id too
└─────────────────────────────┘
```

## Comparison: Numeric IDs vs Asset IDs

| Aspect | Numeric IDs (Current) | Asset IDs (Alternative) |
|--------|----------------------|-------------------------|
| **Storage Size** | 8 bytes (BIGINT) | 128 bytes (VARCHAR) |
| **Index Size** | Small, efficient | 16x larger |
| **Join Performance** | Fast (numeric comparison) | Slower (string comparison) |
| **Composite Keys** | Single column FK | Requires 2 columns (asset_id + site_id) |
| **Referential Integrity** | Easy with DB constraints | More complex |
| **Code Complexity** | Standard JPA | Requires @IdClass or @EmbeddedId |
| **Migration Effort** | N/A (already implemented) | High - all FKs need rewriting |

## Benefits of Current Design

### 1. Performance
- **8 bytes vs 128 bytes**: Numeric IDs are 16x smaller
- **Faster joins**: Integer equality is much faster than string comparison
- **Smaller indexes**: Better memory utilization and faster lookups

### 2. Database Best Practices
- Auto-increment IDs are standard in relational databases
- Simpler foreign key constraints
- Better query optimization by database engines

### 3. Flexibility
- Database IDs are immutable (never change)
- Asset IDs could theoretically change in Tableau
- Easier to refactor relationships without breaking data

### 4. JPA/Hibernate Simplicity
- Standard `@ManyToOne` relationships
- No need for composite key classes
- Cleaner entity code

## Hybrid Approach: Best of Both Worlds

The application uses **both** numeric IDs and asset IDs:

### Numeric IDs For:
- Database relationships (foreign keys)
- Internal joins
- Referential integrity

### Asset IDs For:
- Business logic
- Tableau API integration
- Uniqueness constraints (with site_id)
- Metadata hash generation
- Change detection

## Repository Query Patterns

The codebase supports both lookup patterns:

```java
// Lookup by database FK (fast)
@Query("SELECT r FROM ReportAttribute r WHERE r.dataSource.id = :dsId")
List<ReportAttribute> findByDataSourceDbId(@Param("dsId") Long dsId);

// Lookup by Tableau asset ID (intuitive)
Optional<TableauDataSource> findByAssetIdAndSiteId(String assetId, String siteId);
```

## Common Scenarios

### Scenario 1: Finding all fields from a data source

```sql
-- By numeric FK (fast)
SELECT * FROM report_attribute WHERE datasource_fk_id = 123;

-- By asset ID (intuitive, but requires join)
SELECT ra.* 
FROM report_attribute ra
JOIN tableau_datasource ds ON ra.datasource_fk_id = ds.id
WHERE ds.asset_id = 'abc-123-def' AND ds.site_id = 'my-site';
```

### Scenario 2: Ingestion process

1. Fetch data from Tableau API → returns asset IDs
2. Look up existing entity by asset ID: `findByAssetIdAndSiteId(assetId, siteId)`
3. Create/update entity → database assigns numeric ID
4. Set foreign key using entity reference: `reportAttribute.setDataSource(dataSourceEntity)`
5. JPA automatically uses the numeric ID for the FK

## Why Not Change to Asset IDs?

### Migration Complexity
- All 7 entity classes would need modification
- All foreign key constraints need to be dropped and recreated
- All queries would need to be rewritten
- Risk of data loss or corruption during migration

### Performance Impact
- Larger database size (2-3x increase in index size)
- Slower query performance (string comparisons)
- More memory usage for caching and joins

### Composite Key Complexity
Most entities require `(asset_id, site_id)` for uniqueness, not just `asset_id`:
- ReportAttribute: `(asset_id, worksheet_id, site_id)`
- TableauWorkbook: `(asset_id, site_id)`
- TableauWorksheet: `(asset_id, site_id)`

This would require **two-column foreign keys** everywhere, significantly complicating the schema.

## Conclusion

The current design using numeric IDs as foreign keys is optimal because:

1. **Performance**: Significantly faster than string-based FKs
2. **Best Practices**: Follows standard relational database design
3. **Simplicity**: Easier to maintain and understand
4. **Flexibility**: Hybrid approach gives us both performance and Tableau integration

The recent fix ensures that foreign keys are always synchronized during ingestion, maintaining referential integrity while keeping performance optimal.

## Related Documentation

- [TRACKED_FIELDS.md](TRACKED_FIELDS.md) - Fields used in metadata hash generation
- [TABLE_SCHEMA_REFERENCE.md](TABLE_SCHEMA_REFERENCE.md) - Database schema documentation
- [IDENTIFIER_USAGE.md](IDENTIFIER_USAGE.md) - Information about ID vs LUID usage

---

*Last Updated: 2026-02-01*
