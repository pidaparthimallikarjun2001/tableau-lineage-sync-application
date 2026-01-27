# Quick Guide: Seeing the UPDATED Status

This guide provides a **step-by-step walkthrough** to see the UPDATED status in action.

## Prerequisites

1. Application is running: `mvn spring-boot:run`
2. You're authenticated to Tableau Server
3. You have at least one project with a workbook in Tableau

## Step-by-Step Example

### Step 1: Initial Ingestion

Run the ingestion endpoints in order:

```bash
# Authenticate first
POST http://localhost:8080/api/tableau/auth/signin

# Ingest in hierarchy order
POST http://localhost:8080/api/servers/ingest
POST http://localhost:8080/api/sites/ingest
POST http://localhost:8080/api/projects/ingest
POST http://localhost:8080/api/workbooks/ingest
```

**Result:** All assets will have `status_flag = 'NEW'`

**Verify in H2 Console** (`http://localhost:8080/h2-console`):
```sql
SELECT name, status_flag FROM tableau_workbook;
```

Output:
```
NAME                | STATUS_FLAG
--------------------|------------
My Sales Dashboard  | NEW
Marketing Report    | NEW
```

---

### Step 2: Second Ingestion (No Changes)

Run the same ingestion again **without making any changes** in Tableau:

```bash
POST http://localhost:8080/api/workbooks/ingest
```

**Result:** All workbooks will now have `status_flag = 'ACTIVE'`

**Verify:**
```sql
SELECT name, status_flag FROM tableau_workbook;
```

Output:
```
NAME                | STATUS_FLAG
--------------------|------------
My Sales Dashboard  | ACTIVE
Marketing Report    | ACTIVE
```

**Why ACTIVE?** The metadata hash matches what's in the database, so no changes were detected.

---

### Step 3: Make a Change in Tableau

Now, make a tracked change to one of your workbooks:

**Option A: Rename a Workbook**
1. Open Tableau Server in your browser
2. Navigate to your site
3. Find "My Sales Dashboard"
4. Click the "..." menu → **Rename**
5. Change name to "Sales Dashboard 2024"
6. Click Save

**Option B: Edit Workbook Description**
1. Click on the workbook to open details
2. Click "Edit Description"
3. Add or change the description
4. Click Save

**Option C: Move to Different Project**
1. Select the workbook
2. Click Actions → **Move**
3. Select a different project
4. Click Move

---

### Step 4: Third Ingestion (After Change)

Run the ingestion again:

```bash
POST http://localhost:8080/api/workbooks/ingest
```

**Result:** The changed workbook will have `status_flag = 'UPDATED'` ✓

**Verify:**
```sql
SELECT name, status_flag, metadata_hash FROM tableau_workbook;
```

Output:
```
NAME                   | STATUS_FLAG | METADATA_HASH
-----------------------|-------------|----------------------------------
Sales Dashboard 2024   | UPDATED     | a1b2c3d4e5f6... (new hash)
Marketing Report       | ACTIVE      | 1234567890ab... (same hash)
```

**🎉 SUCCESS!** You can now see the UPDATED status!

---

### Step 5: Fourth Ingestion (No New Changes)

Run the ingestion one more time **without making any new changes**:

```bash
POST http://localhost:8080/api/workbooks/ingest
```

**Result:** The workbook transitions from UPDATED back to ACTIVE

**Verify:**
```sql
SELECT name, status_flag FROM tableau_workbook;
```

Output:
```
NAME                   | STATUS_FLAG
-----------------------|------------
Sales Dashboard 2024   | ACTIVE      (was UPDATED, now ACTIVE)
Marketing Report       | ACTIVE
```

**Why back to ACTIVE?** The new metadata hash now matches what's in the database, indicating the workbook is synchronized with no pending changes.

---

## Complete Lifecycle Visualization

```
Run 1: NEW          (First time ingesting - record created)
         ↓
Run 2: ACTIVE       (No changes detected - synchronized)
         ↓
[Change made in Tableau: Rename workbook]
         ↓
Run 3: UPDATED      (Change detected - hash differs) ← YOU SEE THIS!
         ↓
Run 4: ACTIVE       (No new changes - synchronized again)
```

---

## Using Swagger UI

You can also test this using the Swagger UI at `http://localhost:8080/swagger-ui.html`:

1. Expand "Workbook" section
2. Click on `POST /api/workbooks/ingest`
3. Click "Try it out"
4. Click "Execute"
5. Check the response → look for `updated` count

Example response after Step 4:
```json
{
  "success": true,
  "assetType": "Workbook",
  "totalProcessed": 5,
  "newRecords": 0,
  "updatedRecords": 1,     ← One workbook was updated!
  "deletedRecords": 0,
  "unchangedRecords": 4,
  "message": "Successfully ingested 5 workbooks"
}
```

---

## Troubleshooting

### "I changed something but still see ACTIVE, not UPDATED"

**Possible reasons:**

1. **Changed a non-tracked field**
   - Example: Added tags, changed permissions, or viewed the workbook
   - Solution: Make a tracked change (see TRACKED_FIELDS.md)

2. **Didn't republish from Tableau Desktop**
   - Changes made in Desktop must be published to Server
   - Solution: Publish the workbook to Tableau Server

3. **Changed a different workbook**
   - Make sure you're checking the correct workbook in the database
   - Solution: Verify with `WHERE name = 'Your Workbook Name'`

4. **Ran ingestion twice quickly**
   - The second run transitions UPDATED → ACTIVE
   - Solution: Check the database immediately after Step 4

---

## Testing with Projects (Even Simpler)

Projects are often simpler to test because you can change them directly in Tableau Server:

### Quick Project Test:

1. **Ingest projects:**
   ```bash
   POST http://localhost:8080/api/projects/ingest
   ```
   Result: status = NEW

2. **Ingest again:**
   ```bash
   POST http://localhost:8080/api/projects/ingest
   ```
   Result: status = ACTIVE

3. **In Tableau Server:** Right-click a project → Rename → Change the name

4. **Ingest again:**
   ```bash
   POST http://localhost:8080/api/projects/ingest
   ```
   Result: status = UPDATED ✓

5. **Check database:**
   ```sql
   SELECT name, status_flag FROM tableau_project WHERE name LIKE '%new%';
   ```

---

## Important Note About Worksheets

**⚠️ Worksheets behave differently than other assets!**

When you **rename a worksheet** in Tableau Desktop and republish:
- Tableau assigns a **new ID** to the renamed worksheet
- The old worksheet ID is no longer found → marked as `DELETED`
- The new worksheet ID appears → created as `NEW`

**This is Tableau's behavior**, not a bug. Tableau treats renamed sheets as completely new sheets.

**Result:** Renaming a worksheet shows as `DELETED` + `NEW`, not `UPDATED`.

For more details, see the Worksheet section in **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)**.

---

## Summary

The UPDATED status appears **between the ingestion that detects the change (Step 4) and the next ingestion (Step 5)**. It's a transient state that indicates "this asset was just updated in Tableau."

To see it, you must:
1. ✅ Change a **tracked field** (name, description, owner, etc.)
2. ✅ Run ingestion to detect the change
3. ✅ Check the database **before** running ingestion again

For the complete list of tracked fields for each asset type, see **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)**.
