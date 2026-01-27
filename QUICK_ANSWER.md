# Quick Answer: When Do I See UPDATED Status?

## TL;DR - The Fast Answer

To see **UPDATED** status, you need to:

1. ✅ Use an asset with a **stable ID** (Project or Workbook recommended)
2. ✅ Change a **tracked field** (name, description, owner, etc.)
3. ✅ Run ingestion to detect the change
4. ✅ Check database **before** running ingestion again

## Fastest Way to Test (30 seconds)

```bash
# Step 1: Ingest projects twice
POST /api/projects/ingest  # → Status: NEW
POST /api/projects/ingest  # → Status: ACTIVE

# Step 2: In Tableau Server - Rename a project
# (Right-click project → Rename → Change name)

# Step 3: Ingest again
POST /api/projects/ingest  # → Status: UPDATED ✓

# Step 4: Check in H2 Console
SELECT name, status_flag FROM tableau_project;
```

**You will see UPDATED status!**

## Why Some Assets Show DELETE + NEW Instead

**Your question about worksheets is correct!**

When you **rename a worksheet** in Tableau Desktop:
- Tableau assigns a **new ID** to the worksheet
- Old ID disappears → marked as `DELETED`
- New ID appears → marked as `NEW`
- You do **NOT** see `UPDATED`

**This is Tableau's design**, not a bug in the application.

## Which Assets Can Show UPDATED?

| Asset Type | Shows UPDATED? | Why? |
|------------|----------------|------|
| **Project** ✓ | ✅ Yes | **ID stays same** when renamed |
| **Workbook** ✓ | ✅ Yes | **ID stays same** when renamed |
| **Worksheet** | ❌ No | **ID changes** when renamed |
| Published DataSource | ✅ Yes | ID stays same |
| Site | ✅ Yes | ID stays same |
| Embedded DataSource | ❌ No | ID may change on republish |
| ReportAttribute | ❌ No | ID changes frequently |

## What Changes Trigger UPDATED?

### For Workbooks (Best for Testing)
- ✅ Rename the workbook
- ✅ Edit description
- ✅ Move to different project
- ✅ Change owner
- ❌ NOT: Add tags, change permissions, view the workbook

### For Projects (Easiest to Test)
- ✅ Rename the project
- ✅ Edit description
- ✅ Move to different parent project
- ❌ NOT: Change permissions, add content

### For Worksheets (Shows DELETE + NEW)
- ❌ Rename → **DELETE + NEW** (not UPDATED)
- Because: Tableau changes the worksheet ID

## Complete Documentation

For more details, see:

1. **[SEEING_UPDATED_STATUS.md](SEEING_UPDATED_STATUS.md)** - Step-by-step guide with examples
2. **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)** - What fields are tracked for each asset
3. **[UPDATED_STATUS_EXPLAINED.md](UPDATED_STATUS_EXPLAINED.md)** - Deep dive into Tableau's ID behavior

## Still Can't See UPDATED?

**Common mistakes:**

1. ❌ Changed a non-tracked field (tags, permissions, etc.)
   - **Fix:** Change name or description instead

2. ❌ Changed a worksheet (ID changes)
   - **Fix:** Change a workbook or project instead

3. ❌ Ran ingestion twice quickly
   - **Fix:** Check database immediately after detecting change

4. ❌ Didn't make any real change
   - **Fix:** Actually rename something in Tableau Server

## The Simplest Test Ever

**Do this right now:**

1. Open Tableau Server in your browser
2. Find any project
3. Rename it (add "TEST" to the name)
4. Run: `POST /api/projects/ingest`
5. Check database: `SELECT name, status_flag FROM tableau_project WHERE name LIKE '%TEST%'`

**You will see `status_flag = 'UPDATED'`!**

Then:
6. Run ingestion again: `POST /api/projects/ingest`
7. Check database again
8. Now you'll see `status_flag = 'ACTIVE'`

**That's it!** You've seen the complete lifecycle: NEW → ACTIVE → UPDATED → ACTIVE

---

## Summary

- **UPDATED appears when**: Tracked field changes AND asset has stable ID
- **Best for testing**: Projects and Workbooks (rename them in Server UI)
- **Worksheets don't work**: Tableau changes their ID when renamed
- **Check immediately**: UPDATED transitions to ACTIVE on next ingestion

This is working correctly - it's just about knowing which assets and fields to change!
