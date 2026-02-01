# Tracked Fields for Change Detection

This document explains which fields are tracked for each Tableau asset type to detect changes and set the `UPDATED` status.

## How Change Detection Works

The application uses **metadata hashing** to detect changes:

1. When an asset is ingested, specific fields are combined and hashed using SHA-256
2. The hash is stored in the `metadata_hash` column
3. On subsequent ingestions, a new hash is calculated and compared with the stored hash
4. If the hashes differ, the status is set to `UPDATED`
5. If the hashes match, the status transitions to `ACTIVE`

## What Changes Trigger UPDATED Status?

### Server
**Tracked Fields:**
- Asset ID
- Name (server hostname)
- Server URL
- Version
- Build number

**Example changes that trigger UPDATED:**
- Server version upgrade (e.g., 2023.1 → 2023.2)
- Server URL change
- Build number change

**Changes that DO NOT trigger UPDATED:**
- Server settings/configuration changes
- License changes
- User/group changes

---

### Site
**Tracked Fields:**
- Asset ID
- Name
- Content URL

**Example changes that trigger UPDATED:**
- Rename the site in Tableau Server
- Change the site's content URL

**Changes that DO NOT trigger UPDATED:**
- Site settings (storage quota, revision limits, etc.)
- User/group membership
- Permissions

---

### Project
**Tracked Fields:**
- Asset ID
- Name
- Description
- Parent Project ID (for nested projects)
- Owner (username)
- Site ID

**Example changes that trigger UPDATED:**
1. **Rename the project:**
   - In Tableau: Right-click project → "Rename"
2. **Edit project description:**
   - In Tableau: Right-click project → "Edit Description"
3. **Move project to different parent:**
   - In Tableau: Drag project to another parent project (changes parent project ID)
4. **Change project owner:**
   - In Tableau Server: Select project → Actions → Change Owner

**Changes that DO NOT trigger UPDATED:**
- Project permissions
- Project image/icon
- Content in the project (workbooks/data sources)

---

### Workbook
**Tracked Fields:**
- Asset ID
- Name
- Description
- Project Name (which project the workbook belongs to)
- Owner (username)
- Content URL
- Site ID

**Example changes that trigger UPDATED:**
1. **Rename the workbook:**
   - In Tableau Server: Select workbook → Actions → Rename
2. **Edit workbook description:**
   - In Tableau Server: Open workbook details → Edit Description
3. **Move workbook to different project:**
   - In Tableau Server: Select workbook → Actions → Move → Select different project
4. **Change workbook owner:**
   - In Tableau Server: Select workbook → Actions → Change Owner

**Changes that DO NOT trigger UPDATED:**
- Adding/removing sheets within the workbook
- Editing sheet content (filters, calculations, etc.)
- Changing permissions
- Tags
- View count
- Last accessed time
- Thumbnails

**Important Note:** To see changes in sheets, you need to:
1. Make the change in Tableau Desktop
2. **Republish the workbook** to Tableau Server
3. Run the ingest workflow

---

### Worksheet
**Tracked Fields:**
- Asset ID
- Name
- Workbook ID (which workbook the sheet belongs to)
- Site ID

**⚠️ IMPORTANT LIMITATION FOR WORKSHEETS:**

When you **rename a worksheet** in Tableau Desktop and republish the workbook, Tableau **changes the worksheet's ID**. This means:

- **Old worksheet ID**: Will be marked as `DELETED` (no longer found in Tableau)
- **New worksheet ID**: Will be created with status `NEW`

**This is Tableau's behavior, not a bug in the application.** Tableau treats renamed sheets as new sheets with different IDs.

**Example changes that trigger UPDATED:**
1. **Change workbook that contains the sheet:**
   - If you move a sheet to a different workbook (changes workbook ID)
   - The sheet keeps the same ID but workbook ID changes → status = UPDATED

**Changes that result in DELETE + NEW (not UPDATED):**
1. **Rename the sheet:**
   - In Tableau Desktop: Right-click sheet tab → Rename
   - Publish the workbook to server
   - **Result**: Old sheet ID marked as DELETED, new sheet ID created as NEW
   - **Why**: Tableau assigns a new ID to the renamed sheet

**Changes that DO NOT trigger UPDATED:**
- Sheet content (visualizations, filters, etc.)
- Sheet type (dashboard vs worksheet)
- Hidden/visible status

**Why This Happens:**
Tableau's internal identifier for worksheets is based on the sheet name. When you rename a sheet, Tableau treats it as deleting the old sheet and creating a new one. This is Tableau's design, not a limitation of this application.

---

### Data Source
**Tracked Fields:**

For **Published Data Sources:**
- Asset ID
- Name
- Description
- Is Certified (true/false)
- Owner
- Connection Type
- Table Name
- Upstream Tables
- Site ID

For **Embedded Data Sources:**
- Asset ID
- Name
- Workbook ID
- Connection Type
- Database Name
- Table Names
- Upstream Tables
- Site ID

For **Custom SQL Data Sources:**
- Asset ID
- Name
- Custom SQL Query
- Connection Type
- Database Name
- Upstream Tables
- Site ID

**Example changes that trigger UPDATED:**
1. **Rename the data source:**
   - In Tableau Server: Select data source → Actions → Rename
2. **Edit description:**
   - In Tableau Server: Open data source details → Edit Description
3. **Change certification status:**
   - In Tableau Server: Select data source → Actions → Certify/Uncertify
4. **Change owner:**
   - In Tableau Server: Select data source → Actions → Change Owner
5. **Modify connection details** (for embedded sources):
   - Edit the data source in Tableau Desktop
   - Change connection type or table
   - Republish the workbook
6. **Modify Custom SQL query:**
   - Edit the custom SQL in Tableau Desktop
   - Republish the workbook

**Changes that DO NOT trigger UPDATED:**
- Permissions
- Extract refresh schedule
- Tags
- Last accessed time

---

### Report Attribute (Sheet Fields)
**Tracked Fields:**
- Asset ID
- Name (field name)
- Worksheet ID
- Data Source ID (Tableau asset ID, not database FK)
- Field Role (dimension, measure, etc.)
- Is Calculated (true/false)
- Calculation Logic/Formula
- Lineage Information (upstream fields)
- Site ID

**Note on Foreign Keys:** The metadata hash uses Tableau asset IDs (like `source_datasource_id`) to detect changes, not database foreign key IDs (`datasource_fk_id`, `worksheet_fk_id`). However, foreign key relationships are updated during every ingestion to maintain database referential integrity, regardless of whether the metadata hash has changed.

**Example changes that trigger UPDATED:**
1. **Rename a field:**
   - In Tableau Desktop: Right-click field → Rename
   - Republish workbook
2. **Edit calculation formula:**
   - In Tableau Desktop: Edit calculated field formula
   - Republish workbook
3. **Change field data source:**
   - Modify which data source the field comes from
   - Republish workbook
4. **Change field role:**
   - In Tableau Desktop: Right-click field → Convert to Dimension/Measure
   - Republish workbook

**Changes that DO NOT trigger UPDATED:**
- Field data type
- Default aggregation
- Number format

---

## Testing UPDATED Status

### Quick Test Procedure:

1. **Initial Ingest:**
   ```
   POST /api/servers/ingest
   POST /api/sites/ingest
   POST /api/projects/ingest
   POST /api/workbooks/ingest
   ```
   All assets will have status = `NEW`

2. **Second Ingest (no changes):**
   ```
   POST /api/projects/ingest
   POST /api/workbooks/ingest
   ```
   All assets will have status = `ACTIVE`

3. **Make a tracked change in Tableau:**
   - For a **Project**: Rename it or edit its description
   - For a **Workbook**: Rename it, edit description, or move to different project

4. **Third Ingest:**
   ```
   POST /api/projects/ingest
   POST /api/workbooks/ingest
   ```
   The changed asset will have status = `UPDATED`

5. **Fourth Ingest (no new changes):**
   ```
   POST /api/projects/ingest
   POST /api/workbooks/ingest
   ```
   The previously updated asset will have status = `ACTIVE` again

### Verify in Database:

Access H2 Console at `http://localhost:8080/h2-console`:

```sql
-- Check status of all projects
SELECT id, asset_id, name, status_flag, metadata_hash 
FROM tableau_project 
ORDER BY last_updated_timestamp DESC;

-- Check status of all workbooks
SELECT id, asset_id, name, status_flag, metadata_hash 
FROM tableau_workbook 
ORDER BY last_updated_timestamp DESC;

-- Find all UPDATED assets
SELECT 'Project' as type, name, status_flag FROM tableau_project WHERE status_flag = 'UPDATED'
UNION ALL
SELECT 'Workbook' as type, name, status_flag FROM tableau_workbook WHERE status_flag = 'UPDATED'
UNION ALL
SELECT 'Site' as type, name, status_flag FROM tableau_site WHERE status_flag = 'UPDATED';
```

---

## Common Mistakes

### ❌ Changes That Won't Trigger UPDATED:

1. **Editing a sheet's visualization** without republishing
   - The changes stay in Tableau Desktop only

2. **Changing permissions or tags**
   - These fields are not tracked in the metadata hash

3. **Viewing or accessing a workbook**
   - This updates last accessed time, but not tracked fields

4. **Adding comments**
   - Comments are not tracked in the metadata hash

### ✅ Changes That Will Trigger UPDATED:

1. **Rename any asset** (project, workbook, data source, etc.)
2. **Edit description** of an asset
3. **Move asset to different parent** (workbook to different project, project to different parent)
4. **Change ownership**
5. **Certify/uncertify a data source**
6. **Modify SQL query** in a custom SQL data source and republish

---

## Why Some Fields Are Not Tracked

The application tracks **metadata fields** that define the structure and identity of assets, not:
- **Operational fields**: View counts, last accessed time, usage statistics
- **Configuration fields**: Permissions, schedules, subscriptions
- **Content fields**: The actual visualizations, filters within sheets

This design choice keeps the change detection focused on meaningful structural changes that affect lineage and metadata relationships.

---

## Need to Track Additional Fields?

If you need to track additional fields for your use case, you can modify the `generateMetadataHash()` calls in each service.

**Example:** Add tags to workbook hash in WorkbookService.java:

```java
// Modify the hash generation to include tags
String newHash = generateMetadataHash(assetId, name, description, projectName, 
        owner, contentUrl, tags, currentSiteId);  // Add tags parameter
```

Remember to also extract and store the additional field in the entity.
