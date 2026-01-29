# Batch Processing Limitations and Recommendations

## Problem Statement

When ingesting assets to Collibra, if the number of assets exceeds the configured batch size, they are split into multiple batches for memory management. However, this creates a fundamental issue with cross-batch dependencies:

### The Cross-Batch Dependency Problem

**Assets within the same type can have dependencies on each other:**
- **Projects**: Child projects depend on parent projects
- **Report Attributes**: Calculated fields depend on other fields (calculated or non-calculated)

**When split across batches, dependencies may fail:**

```
Batch 1 [Assets 1-2000]:   Import to Collibra → Some relations may fail
Batch 2 [Assets 2001-4000]: Import to Collibra → Some relations may fail  
Batch 3 [Assets 4001-5000]: Import to Collibra → Some relations may fail
```

**Why relations fail:**
- Each batch is sent as a separate API call to Collibra Import API
- Each API call is a separate import job
- Assets in Batch 1 cannot reference assets in Batch 2 (they don't exist yet in Collibra)
- Assets in Batch 3 cannot reference assets in Batch 1 or 2 (already imported, but forward references don't work across jobs)

### What We've Done to Minimize the Issue

1. **Deferred Deletions** ✓
   - Deletions now happen AFTER all asset types are imported
   - Prevents "asset not found" errors when later asset types reference earlier deleted assets

2. **Dependency-Aware Ordering** ✓
   - Projects are sorted: parent projects before child projects
   - Report Attributes are sorted: non-calculated fields before calculated fields
   - This minimizes (but doesn't eliminate) cross-batch dependency failures

3. **Warning Logs** ✓
   - System logs warnings when batching occurs
   - Alerts you to potential relation failures

## Solutions and Trade-offs

### Option 1: Increase Batch Size (Risk: OutOfMemoryError)

**When to use:**
- You have sufficient heap memory (8GB+ recommended)
- Asset count per type is < 10,000

**Configuration:**
```properties
# In application.properties
collibra.batch.size=10000
```

**Pros:**
- Fewer batches = fewer cross-batch dependency issues
- All assets of same type imported in fewer jobs

**Cons:**
- Higher memory usage
- Risk of OutOfMemoryError with large datasets
- May need to increase JVM heap: `java -Xmx8g -jar app.jar`

### Option 2: Decrease Batch Size (Risk: Relation Failures)

**When to use:**
- Limited memory (< 4GB heap)
- Experiencing OutOfMemoryError with current batch size

**Configuration:**
```properties
# In application.properties
collibra.batch.size=1000
```

**Pros:**
- Lower memory usage
- Can handle large datasets without crashing

**Cons:**
- More batches = higher chance of relation failures
- Some calculated fields may not link correctly
- Some child projects may not link to parents

### Option 3: Balanced Approach (Recommended)

**Configuration:**
```properties
# Default - balances memory and dependencies
collibra.batch.size=2000
```

**Additional JVM Settings:**
```bash
# Run with adequate heap
java -Xmx4g -Xms2g -jar tableau-lineage-sync-application.jar
```

**Monitor logs for:**
- Warnings about batching occurring
- Collibra import errors related to missing assets
- Check Collibra UI for assets with incomplete relations

### Option 4: Re-import After Initial Load

**Process:**
1. Initial import with batching (some relations may fail)
2. Re-run ingestion for problematic asset types
   - Projects: `POST /api/collibra/ingest/projects`
   - Report Attributes: `POST /api/collibra/ingest/report-attributes`
3. Second run will update existing assets and retry failed relations

**Why this works:**
- All assets already exist in Collibra from first run
- Second run uses `existingAssetPolicy: UPDATE` 
- Failed relations can now succeed because target assets exist

## Monitoring and Troubleshooting

### Check Logs for Batching

When you see this log message:
```
WARN  CollibraRestClient - Asset count (5500) exceeds batch size (2000). 
Assets will be split across 3 batches. If assets have dependencies on each other, 
ensure they are pre-ordered with dependencies last. Some relations may fail if 
dependencies span across batches.
```

**Action:** Consider increasing batch size or plan for re-import

### Check Collibra for Missing Relations

After ingestion, check in Collibra UI:
1. Go to problematic calculated fields
2. Check if "derived from" relations exist
3. Check child projects for "parent project" relations

**If relations are missing:**
- Re-run the specific asset type ingestion
- Check Collibra import job logs for error details

### Memory Monitoring

Monitor JVM heap usage:
```bash
# Check heap during ingestion
jmap -heap <pid>

# Or use JVM flags
java -Xmx4g -XX:+PrintGCDetails -jar app.jar
```

**Signs you need more memory:**
- OutOfMemoryError exceptions
- Frequent garbage collection
- Import jobs timing out

**Signs you can reduce batch size:**
- Heap usage < 50% during import
- No memory errors
- Want to reduce relation failures

## Best Practices

### For Small to Medium Deployments (< 5,000 assets per type)

```properties
collibra.batch.size=5000
```
```bash
java -Xmx4g -jar app.jar
```

### For Large Deployments (> 5,000 assets per type)

**Option A: High Memory**
```properties
collibra.batch.size=10000
```
```bash
java -Xmx8g -jar app.jar
```

**Option B: Lower Memory + Re-import Strategy**
```properties
collibra.batch.size=2000
```
```bash
java -Xmx4g -jar app.jar
```
Then re-import after initial load completes.

### Site-by-Site Ingestion (Recommended for Large Deployments)

Instead of ingesting all assets at once, ingest by site:
```bash
POST /api/collibra/ingest/sites/{siteId}/all
```

**Benefits:**
- Smaller asset counts per call
- Less likely to exceed batch size
- Better memory management
- Easier to track progress

## Future Enhancements (Not Yet Implemented)

### Two-Phase Import Approach

**Phase 1:** Import all assets without relations (batched)
**Phase 2:** Add/update relations only (batched)

This would completely solve the cross-batch dependency issue but requires:
- Significant code changes
- Separate API calls for asset creation vs relation updates
- More complex error handling

### Intelligent Dependency Graph

Build a dependency graph and import in topological order:
- Guarantees dependencies are satisfied
- May require multiple passes
- Complex implementation

## Summary

**The batch processing limitation is a fundamental trade-off:**
- **Smaller batches** = Lower memory usage, but more relation failures
- **Larger batches** = Fewer relation failures, but higher memory usage

**Our implementation:**
- ✅ Defers deletions to prevent cross-asset-type dependency failures
- ✅ Orders assets by dependency to minimize (not eliminate) issues
- ✅ Provides configuration options for your specific environment
- ✅ Logs warnings when batching occurs
- ⚠️ Accepts that some relations may fail with very large datasets

**Recommended approach:**
1. Start with default batch size (2000)
2. Monitor logs and Collibra for issues
3. Adjust batch size based on your memory and data size
4. Use site-by-site ingestion for large deployments
5. Re-import if relations are missing
