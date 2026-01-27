# Understanding UPDATED Status: Which Assets Support It?

This document explains **which Tableau assets can show UPDATED status** and which ones show DELETE + NEW instead when modified.

## TL;DR - Quick Answer

| Asset Type | Can Show UPDATED? | Why or Why Not |
|------------|-------------------|----------------|
| **Server** | ✅ Yes | ID stays same when server is upgraded |
| **Site** | ✅ Yes | ID stays same when site is renamed |
| **Project** | ✅ Yes | ID stays same when project is renamed or moved |
| **Workbook** | ✅ Yes | ID stays same when workbook is renamed or modified |
| **Worksheet** | ❌ No* | **Tableau changes ID when sheet is renamed** |
| **Data Source** | ✅ Yes (Published)<br>❌ No* (Embedded) | Published DS: ID stable<br>Embedded DS: ID may change on republish |
| **Report Attribute** | ❌ No* | Field IDs change when sheet/workbook is republished |

**\*Note:** These assets show `DELETED` + `NEW` instead of `UPDATED` when modified due to Tableau's ID assignment behavior.

---

## The Core Issue: Tableau's ID Assignment

### Stable IDs (Support UPDATED)

Some Tableau assets have **stable, persistent IDs** that don't change when you modify the asset:

✅ **Server, Site, Project, Workbook, Published Data Sources**

- These assets are **first-class objects** in Tableau
- Their IDs are assigned when created and **remain stable** throughout their lifetime
- You can rename them, modify them, move them → ID stays the same
- **Result:** This application can detect changes via metadata hash → status = UPDATED

**Example: Workbook**
```
1. Create workbook "Sales Report" → ID = "abc123"
2. Rename to "Sales Dashboard" → ID still "abc123" ✓
3. Change description → ID still "abc123" ✓
4. Move to different project → ID still "abc123" ✓
```

**Application detects:** Metadata hash changes → status = UPDATED

---

### Volatile IDs (Show DELETE + NEW)

Some Tableau assets have **volatile IDs** that change when the asset is modified:

❌ **Worksheets, Embedded Data Sources, Report Attributes**

- These assets are **nested/embedded objects** within workbooks
- Their IDs are **derived from their content or name**
- When you modify them, Tableau assigns a **new ID**
- **Result:** This application sees the old ID disappear and a new ID appear → DELETED + NEW

**Example: Worksheet**
```
1. Create worksheet "Dashboard" → ID = "sheet-abc123"
2. Rename to "Overview" → ID changes to "sheet-xyz789" ✗
   - Old ID "sheet-abc123" no longer in Tableau → marked DELETED
   - New ID "sheet-xyz789" appears → marked NEW
```

**Application detects:** Old ID missing, new ID found → DELETED + NEW

---

## Why Does Tableau Do This?

### Worksheets

Tableau's worksheet IDs are **name-based or content-based**. When you rename a worksheet:

1. Tableau Desktop internally treats it as deleting the old sheet
2. Creates a new sheet with the new name and a new ID
3. On publish, the server receives the delete + create operations

**This is by design in Tableau**, not a bug in this application.

### Embedded Data Sources

Embedded data sources (data sources inside a workbook, not published separately) have IDs that:

1. May be regenerated when the workbook is republished
2. Are tied to the workbook's internal structure
3. Change when the data source definition changes significantly

### Report Attributes (Sheet Fields)

Sheet field instances (the fields used in a sheet) have IDs that:

1. Are based on the field's lineage and usage context
2. Change when the sheet or data source is modified
3. Are regenerated on workbook republish

---

## Practical Implications

### ✅ Best Assets for Testing UPDATED Status

1. **Projects** (easiest)
   - Rename directly in Tableau Server
   - No republish needed
   - ID always stable

2. **Workbooks**
   - Rename, edit description, or move to different project in Tableau Server
   - No republish needed for these changes
   - ID always stable

3. **Published Data Sources**
   - Rename, edit description, or certify in Tableau Server
   - ID stable for published sources

### ❌ Assets That Won't Show UPDATED

1. **Worksheets**
   - Renaming → DELETE + NEW
   - Best for testing deletion/creation detection

2. **Embedded Data Sources**
   - May show DELETE + NEW on republish
   - Depends on how Tableau regenerates IDs

3. **Report Attributes**
   - Frequently show DELETE + NEW
   - Very sensitive to republish operations

---

## Testing Recommendations

### To See UPDATED Status: ✅

**Use Projects or Workbooks**

```bash
# Test with Projects (simplest)
1. POST /api/projects/ingest → NEW
2. POST /api/projects/ingest → ACTIVE
3. Rename project in Tableau Server
4. POST /api/projects/ingest → UPDATED ✓

# Test with Workbooks
1. POST /api/workbooks/ingest → NEW
2. POST /api/workbooks/ingest → ACTIVE
3. Rename workbook or edit description in Tableau Server
4. POST /api/workbooks/ingest → UPDATED ✓
```

### To See DELETE + NEW: ❌

**Use Worksheets**

```bash
1. POST /api/worksheets/ingest → NEW
2. POST /api/worksheets/ingest → ACTIVE
3. Rename worksheet in Tableau Desktop and republish
4. POST /api/worksheets/ingest → Old sheet: DELETED, New sheet: NEW
```

---

## FAQ

### Q: Can I make worksheets show UPDATED instead of DELETE + NEW?

**A: Not without changing Tableau's behavior.** 

The issue is that Tableau itself assigns new IDs to renamed worksheets. This application tracks assets by their ID (as provided by Tableau). When the ID changes, the application correctly sees it as a different asset.

**Potential workarounds** (not implemented):

1. **Track by composite key** (name + workbook + position)
   - Problem: Names can be duplicated, sheets can move
   - Would create false matches

2. **Use fuzzy matching** (similar name in same workbook)
   - Problem: Could incorrectly match different sheets
   - Not reliable for lineage tracking

3. **Store additional identifiers**
   - Problem: Tableau doesn't provide stable secondary IDs for sheets

**Recommendation:** Accept this as Tableau's design. Use DELETE + NEW patterns for worksheets.

---

### Q: Why does the hash include the asset ID if it changes?

**A: The hash is for detecting changes to the SAME asset.**

```java
// WorksheetService.java line 105
String newHash = generateMetadataHash(assetId, name, workbookId, currentSiteId);
```

The `assetId` is included in the hash to:
1. Ensure uniqueness across different assets
2. Detect if Tableau reassigns an ID (which it shouldn't for stable assets)
3. Create a comprehensive fingerprint of the asset's identity and metadata

For assets with **stable IDs** (workbooks, projects), this works perfectly:
- Same ID → compare hashes → detect metadata changes → UPDATED

For assets with **volatile IDs** (worksheets), this correctly treats renamed items as different assets:
- Different ID → different asset → old one DELETED, new one NEW

---

### Q: Is this a bug in the application?

**A: No, this is correct behavior.**

The application correctly implements change detection based on Tableau's ID assignment:

1. **For stable IDs** (workbooks, projects): Detects UPDATED when metadata changes ✓
2. **For volatile IDs** (worksheets): Detects DELETE + NEW when ID changes ✓

Both behaviors are correct and reflect how Tableau actually manages these assets.

---

## Summary Table: When You'll See Each Status

| Status | Condition | Example |
|--------|-----------|---------|
| **NEW** | Asset ID appears for first time | First ingestion of any asset |
| **ACTIVE** | Asset ID found, hash matches | Second ingestion with no changes |
| **UPDATED** | Asset ID found, hash differs | Rename a **workbook** (ID stable, hash changes) |
| **DELETED** | Asset ID not found in Tableau | Asset deleted, or **worksheet renamed** (old ID gone) |

---

## Recommendations

### For Change Tracking

1. **Use Workbooks and Projects** for reliable UPDATED detection
2. **Accept DELETE + NEW for Worksheets** as normal behavior
3. **Focus on Published Data Sources** rather than embedded ones

### For Lineage Tracking

1. **Parent-child relationships** (Workbook → Worksheet) are stable
   - Even though worksheet IDs change, the relationship structure remains
   - Track lineage at the workbook level when possible

2. **Published Data Sources** provide stable lineage
   - Use these for critical lineage tracking
   - Less disruption from republish operations

3. **Report Attributes** are inherently volatile
   - Expect frequent DELETE + NEW cycles
   - Use for point-in-time analysis rather than change tracking

---

## Related Documentation

- **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)** - Detailed list of tracked fields for each asset type
- **[SEEING_UPDATED_STATUS.md](SEEING_UPDATED_STATUS.md)** - Step-by-step guide with examples
- **[IDENTIFIER_USAGE.md](IDENTIFIER_USAGE.md)** - How IDs vs LUIDs are used

---

*This is a design characteristic of Tableau Server's architecture, not a limitation of this application. The application correctly reflects Tableau's ID assignment behavior.*
