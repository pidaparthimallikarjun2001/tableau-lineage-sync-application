# Site Soft Deletion - Cascade Behavior

## Overview

When a **Site** is soft deleted in the Tableau Lineage Sync Application, it triggers a **cascading soft deletion** that propagates through the entire hierarchy of child assets. This document explains what gets soft deleted in the database when a site is soft deleted.

## What is Soft Deletion?

**Soft deletion** means the record remains in the database but is marked as logically deleted by setting:
- `statusFlag = StatusFlag.DELETED`
- `collibraSyncStatus` is updated appropriately

The actual data is **not physically removed** from the database, allowing for:
- Historical tracking and audit trails
- Potential recovery if needed
- Maintaining referential integrity
- Collibra synchronization tracking

## Site Soft Deletion Cascade

When a **Site** is soft deleted, the following entities are automatically soft deleted in a cascading manner:

### Hierarchy of Soft Deletion

```
Site (DELETED)
 ├─► All Projects in the Site (DELETED)
 │    └─► All Workbooks in each Project (DELETED)
 │         ├─► All Worksheets in each Workbook (DELETED)
 │         │    └─► All Report Attributes in each Worksheet (DELETED)
 │         └─► All Data Sources in each Workbook (DELETED)
```

### Entities Affected

When you soft delete a **Site**, the following entities are soft deleted:

| Order | Entity | Description | Method Called |
|-------|--------|-------------|---------------|
| 1 | **Site** | The site itself | `SiteService.softDeleteSiteAndChildren()` |
| 2 | **Projects** | All projects belonging to the site | `ProjectService.softDeleteProjectsForSite()` |
| 3 | **Workbooks** | All workbooks in each project | `WorkbookService.softDeleteWorkbooksForProject()` |
| 4 | **Worksheets** | All worksheets in each workbook | `WorksheetService.softDeleteWorksheetsForWorkbook()` |
| 5 | **Report Attributes** | All report attributes (fields) in each worksheet | `ReportAttributeService.softDeleteReportAttributesForWorksheet()` |
| 6 | **Data Sources** | All data sources embedded in each workbook | `DataSourceService.softDeleteDataSourcesForWorkbook()` |

## Implementation Details

### Code Flow

The soft deletion process follows this sequence:

#### 1. Site Deletion
```java
// SiteService.java - Line 177-189
@Transactional
public void softDeleteSiteAndChildren(Long siteId) {
    Optional<TableauSite> siteOpt = siteRepository.findById(siteId);
    if (siteOpt.isPresent()) {
        TableauSite site = siteOpt.get();
        site.setStatusFlag(StatusFlag.DELETED);
        site.setCollibraSyncStatus(determineCollibraSyncStatus(StatusFlag.DELETED, site.getCollibraSyncStatus()));
        siteRepository.save(site);
        log.info("Soft deleted site: {} and cascading to children", site.getName());
        
        // Cascade to projects
        projectService.softDeleteProjectsForSite(site.getAssetId());
    }
}
```

#### 2. Projects for Site
```java
// ProjectService.java - Line 218-223
@Transactional
public void softDeleteProjectsForSite(String siteId) {
    List<TableauProject> projects = projectRepository.findBySiteId(siteId);
    for (TableauProject project : projects) {
        softDeleteProjectAndChildren(project.getId());
    }
}
```

#### 3. Workbooks for Each Project
```java
// WorkbookService.java - Line 227-232
@Transactional
public void softDeleteWorkbooksForProject(Long projectId) {
    List<TableauWorkbook> workbooks = workbookRepository.findByProjectDbId(projectId);
    for (TableauWorkbook workbook : workbooks) {
        softDeleteWorkbookAndChildren(workbook.getId());
    }
}
```

#### 4. Worksheets and Data Sources for Each Workbook
```java
// WorkbookService.java - Line 209-220
@Transactional
public void softDeleteWorkbookAndChildren(Long workbookId) {
    Optional<TableauWorkbook> workbookOpt = workbookRepository.findById(workbookId);
    if (workbookOpt.isPresent()) {
        TableauWorkbook workbook = workbookOpt.get();
        workbook.setStatusFlag(StatusFlag.DELETED);
        workbook.setCollibraSyncStatus(determineCollibraSyncStatus(StatusFlag.DELETED, workbook.getCollibraSyncStatus()));
        workbookRepository.save(workbook);
        log.info("Soft deleted workbook: {} and cascading to children", workbook.getName());
        
        // Cascade to worksheets and embedded data sources
        worksheetService.softDeleteWorksheetsForWorkbook(workbookId);
        dataSourceService.softDeleteDataSourcesForWorkbook(workbookId);
    }
}
```

#### 5. Report Attributes for Each Worksheet
```java
// WorksheetService.java - Line 182-193
@Transactional
public void softDeleteWorksheetAndChildren(Long worksheetId) {
    Optional<TableauWorksheet> worksheetOpt = worksheetRepository.findById(worksheetId);
    if (worksheetOpt.isPresent()) {
        TableauWorksheet worksheet = worksheetOpt.get();
        worksheet.setStatusFlag(StatusFlag.DELETED);
        worksheet.setCollibraSyncStatus(determineCollibraSyncStatus(StatusFlag.DELETED, worksheet.getCollibraSyncStatus()));
        worksheetRepository.save(worksheet);
        log.info("Soft deleted worksheet: {} and cascading to children", worksheet.getName());
        
        // Cascade to report attributes
        reportAttributeService.softDeleteReportAttributesForWorksheet(worksheetId);
    }
}
```

## API Endpoint

### Manual Site Soft Deletion

You can manually trigger site soft deletion via the REST API:

```http
DELETE /api/sites/{id}
```

**Parameters:**
- `id` (path parameter) - The database ID (primary key) of the site

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/sites/1
```

**Response:**
- HTTP 204 (No Content) on success
- HTTP 404 (Not Found) if site doesn't exist

### Automatic Soft Deletion During Ingestion

Sites are also **automatically soft deleted** during the ingestion process when:
- A site exists in the database but is **no longer found in Tableau**
- The ingestion process detects that the site has been removed from the Tableau Server

```java
// SiteService.java - Line 150-158
// Soft delete sites not found in current response
List<TableauSite> existingSites = siteRepository.findByServerDbId(server.getId());
for (TableauSite existingSite : existingSites) {
    if (existingSite.getStatusFlag() != StatusFlag.DELETED &&
        fetchedSiteIds.stream().noneMatch(id -> id.equals(existingSite.getAssetId()))) {
        log.info("Site {} no longer exists in Tableau, soft deleting", existingSite.getName());
        softDeleteSiteAndChildren(existingSite.getId());
    }
}
```

## Transaction Safety

All soft deletion operations are marked with `@Transactional` annotations, ensuring:
- **Atomicity**: Either all entities in the cascade are soft deleted, or none are
- **Consistency**: Database integrity is maintained throughout the cascade
- **Rollback**: If any part of the cascade fails, all changes are rolled back

## Database Impact

### Tables Affected

When a site is soft deleted, the following database tables are updated:

1. **tableau_site** - The site record itself
2. **tableau_project** - All projects in the site
3. **tableau_workbook** - All workbooks in those projects
4. **tableau_worksheet** - All worksheets in those workbooks
5. **report_attribute** - All report attributes (fields) in those worksheets
6. **tableau_datasource** - All data sources embedded in those workbooks

### Fields Updated Per Record

For each soft deleted record, the following fields are updated:

- `status_flag` → `'DELETED'`
- `collibra_sync_status` → Updated based on current Collibra sync state
- `last_updated_timestamp` → Current timestamp (auto-updated by JPA)

### Example SQL to View Soft Deleted Records

```sql
-- View all soft deleted sites
SELECT id, asset_id, name, status_flag, last_updated_timestamp
FROM tableau_site
WHERE status_flag = 'DELETED';

-- Count entities soft deleted for a specific site
SELECT 
    (SELECT COUNT(*) FROM tableau_project WHERE site_id = 'site-asset-id' AND status_flag = 'DELETED') AS deleted_projects,
    (SELECT COUNT(*) FROM tableau_workbook WHERE site_id = 'site-asset-id' AND status_flag = 'DELETED') AS deleted_workbooks,
    (SELECT COUNT(*) FROM tableau_worksheet w 
     INNER JOIN tableau_workbook wb ON w.workbook_db_id = wb.id 
     WHERE wb.site_id = 'site-asset-id' AND w.status_flag = 'DELETED') AS deleted_worksheets,
    (SELECT COUNT(*) FROM tableau_datasource WHERE site_id = 'site-asset-id' AND status_flag = 'DELETED') AS deleted_datasources;
```

## Important Notes

### What is NOT Deleted

1. **Server records** - The Tableau Server entity is not affected by site deletion
2. **Physical data** - No records are physically removed from the database
3. **Database relationships** - Foreign key relationships remain intact

### Collibra Integration

When assets are soft deleted:
- The `collibraSyncStatus` field is updated to reflect the deletion
- This allows Collibra integration to be notified of deleted assets
- The integration can then update or remove corresponding assets in Collibra

### Recovery

While soft deletion is designed to be permanent from an application perspective:
- Records can potentially be recovered by changing `statusFlag` back to `ACTIVE`
- However, this should be done carefully and is not officially supported
- It's recommended to treat soft deleted records as archived data

### Performance Considerations

- Soft deletion of a large site can affect many records (hundreds or thousands)
- The cascade operation is performed in a single transaction
- For very large sites, this operation may take several seconds to complete
- Database indexes on `site_id`, `project_db_id`, and `workbook_db_id` columns help optimize cascade queries

## Example Scenarios

### Scenario 1: Small Site
**Given:** A site with 2 projects, 5 workbooks, 20 worksheets, 100 report attributes, and 5 data sources

**When:** The site is soft deleted

**Then:** A total of **133 records** are marked as `DELETED`:
- 1 site
- 2 projects
- 5 workbooks
- 20 worksheets
- 100 report attributes
- 5 data sources

### Scenario 2: Large Enterprise Site
**Given:** A site with 50 projects, 500 workbooks, 5,000 worksheets, 50,000 report attributes, and 250 data sources

**When:** The site is soft deleted

**Then:** A total of **55,801 records** are marked as `DELETED`:
- 1 site
- 50 projects
- 500 workbooks
- 5,000 worksheets
- 50,000 report attributes
- 250 data sources

## Related Documentation

- **[README.md](README.md)** - Main application documentation
- **[TABLE_SCHEMA_REFERENCE.md](TABLE_SCHEMA_REFERENCE.md)** - Database schema details
- **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)** - Fields that trigger change detection

## Summary

**Q: While soft deleting a site, what all are soft deleted in the database?**

**A:** When a site is soft deleted:
1. ✅ The **Site** itself
2. ✅ All **Projects** in the site
3. ✅ All **Workbooks** in those projects
4. ✅ All **Worksheets** in those workbooks
5. ✅ All **Report Attributes** (fields/columns) in those worksheets
6. ✅ All **Data Sources** embedded in those workbooks

All records remain in the database but are marked with `statusFlag = DELETED`, maintaining data integrity and audit trails while logically removing them from active use.
