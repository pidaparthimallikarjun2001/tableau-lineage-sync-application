# Table Schema Reference: Report_Attribute and Tableau_DataSource

This document provides detailed explanations of the database columns in the `report_attribute` and `tableau_datasource` tables, including their purpose, data types, and practical examples.

---

## Table: `report_attribute`

The `report_attribute` table stores information about **field instances** used in Tableau worksheets. Each record represents a field (dimension, measure, or calculated field) that appears in a specific worksheet, along with its lineage and calculation information.

### Overview

**Purpose**: Track which fields are used in worksheets, their data sources, calculations, and lineage relationships.

**Entity Class**: `com.example.tableau.entity.ReportAttribute`

**Database Table Name**: `report_attribute`

### Column Definitions

| Column Name | Data Type | Nullable | Description | Example Value |
|-------------|-----------|----------|-------------|---------------|
| `id` | BIGINT | No | Auto-generated primary key | `1`, `2`, `3` |
| `asset_id` | VARCHAR(128) | No | Unique identifier from Tableau (ID, not LUID) | `123e4567-e89b-12d3-a456-426614174000` |
| `worksheet_id` | VARCHAR(128) | Yes | ID of the parent worksheet | `567e4567-e89b-12d3-a456-426614174999` |
| `site_id` | VARCHAR(128) | Yes | ID of the Tableau site (for uniqueness) | `site-123` |
| `name` | VARCHAR(512) | No | Name of the field | `Sales Amount`, `Customer Count`, `Profit Ratio` |
| `data_type` | VARCHAR(64) | Yes | Data type of the field | `INTEGER`, `STRING`, `REAL`, `BOOLEAN`, `DATE` |
| `field_role` | VARCHAR(64) | Yes | Role of the field in Tableau | `DIMENSION`, `MEASURE`, `UNKNOWN` |
| `is_calculated` | BOOLEAN | Yes | Whether this is a calculated field | `true`, `false` |
| `calculation_logic` | TEXT | Yes | Formula for calculated fields | `SUM([Sales]) / SUM([Quantity])` |
| `source_datasource_id` | VARCHAR(128) | Yes | ID of the source data source | `ds-789e4567-e89b-12d3` |
| `source_datasource_name` | VARCHAR(512) | Yes | Name of the source data source | `Sales Database`, `Customer Analytics` |
| `source_column_name` | VARCHAR(512) | Yes | Original column name from source | `sales_amt`, `customer_id`, `order_date` |
| `source_table_name` | VARCHAR(512) | Yes | Source table name | `fact_sales`, `dim_customer` |
| `lineage_info` | TEXT | Yes | Complete lineage as JSON | `{"upstreamFields": [...], "upstreamTables": [...]}` |
| `status_flag` | VARCHAR(20) | No | Change tracking status | `NEW`, `UPDATED`, `DELETED`, `ACTIVE` |
| `metadata_hash` | VARCHAR(128) | Yes | SHA-256 hash for change detection | `abc123def456...` |
| `created_timestamp` | TIMESTAMP | No | When the record was created | `2024-01-15 10:30:00` |
| `last_updated_timestamp` | TIMESTAMP | No | When the record was last updated | `2024-01-20 14:45:00` |
| `worksheet_fk_id` | BIGINT | Yes | Foreign key to `tableau_worksheet.id` | `5` |
| `datasource_fk_id` | BIGINT | Yes | Foreign key to `tableau_datasource.id` | `3` |

### Unique Constraint

The table has a unique constraint on: `(asset_id, worksheet_id, site_id)`

This ensures a field instance is unique within a worksheet for a specific site.

### Examples

#### Example 1: Simple Direct Field Mapping

A non-calculated field that maps directly to a database column:

```sql
INSERT INTO report_attribute VALUES (
    1,                                    -- id
    'field-001',                          -- asset_id
    'worksheet-123',                      -- worksheet_id
    'site-main',                          -- site_id
    'Sales Amount',                       -- name
    'REAL',                               -- data_type
    'MEASURE',                            -- field_role
    false,                                -- is_calculated
    NULL,                                 -- calculation_logic (not calculated)
    'datasource-456',                     -- source_datasource_id
    'Sales Database',                     -- source_datasource_name
    'sales_amt',                          -- source_column_name
    'fact_sales',                         -- source_table_name
    '{"upstreamFields": [{"name": "sales_amt", "table": "fact_sales"}]}', -- lineage_info
    'ACTIVE',                             -- status_flag
    'a1b2c3d4e5f6...',                   -- metadata_hash
    '2024-01-15 10:00:00',               -- created_timestamp
    '2024-01-15 10:00:00',               -- last_updated_timestamp
    10,                                   -- worksheet_fk_id
    5                                     -- datasource_fk_id
);
```

**Use Case**: This represents the "Sales Amount" measure in a sales dashboard, directly pulling from the `sales_amt` column in the `fact_sales` table.

#### Example 2: Calculated Field with Formula

A calculated field that computes a ratio:

```sql
INSERT INTO report_attribute VALUES (
    2,                                    -- id
    'field-002',                          -- asset_id
    'worksheet-123',                      -- worksheet_id
    'site-main',                          -- site_id
    'Profit Margin %',                    -- name
    'REAL',                               -- data_type
    'MEASURE',                            -- field_role
    true,                                 -- is_calculated
    'SUM([Profit]) / SUM([Sales]) * 100', -- calculation_logic
    'datasource-456',                     -- source_datasource_id
    'Sales Database',                     -- source_datasource_name
    NULL,                                 -- source_column_name (calculated, no direct mapping)
    NULL,                                 -- source_table_name (calculated from multiple sources)
    '{"upstreamFields": [{"name": "profit", "table": "fact_sales"}, {"name": "sales_amt", "table": "fact_sales"}]}',
    'ACTIVE',                             -- status_flag
    'x9y8z7w6v5u4...',                   -- metadata_hash
    '2024-01-15 11:30:00',               -- created_timestamp
    '2024-01-15 11:30:00',               -- last_updated_timestamp
    10,                                   -- worksheet_fk_id
    5                                     -- datasource_fk_id
);
```

**Use Case**: This represents a calculated field "Profit Margin %" that computes the profit margin percentage by dividing total profit by total sales. The `calculation_logic` contains the Tableau formula, and `lineage_info` shows it depends on both `profit` and `sales_amt` fields.

#### Example 3: Dimension Field from Customer Table

A dimension field used for filtering or grouping:

```sql
INSERT INTO report_attribute VALUES (
    3,                                    -- id
    'field-003',                          -- asset_id
    'worksheet-124',                      -- worksheet_id
    'site-main',                          -- site_id
    'Customer Region',                    -- name
    'STRING',                             -- data_type
    'DIMENSION',                          -- field_role
    false,                                -- is_calculated
    NULL,                                 -- calculation_logic
    'datasource-457',                     -- source_datasource_id
    'Customer Dimension',                 -- source_datasource_name
    'region',                             -- source_column_name
    'dim_customer',                       -- source_table_name
    '{"upstreamFields": [{"name": "region", "table": "dim_customer"}]}',
    'ACTIVE',                             -- status_flag
    'p0o9i8u7y6t5...',                   -- metadata_hash
    '2024-01-16 09:15:00',               -- created_timestamp
    '2024-01-16 09:15:00',               -- last_updated_timestamp
    11,                                   -- worksheet_fk_id
    6                                     -- datasource_fk_id
);
```

**Use Case**: This represents the "Customer Region" dimension field from the customer dimension table, typically used for filtering or breaking down metrics by geographic region.

### Querying Examples

#### Find All Calculated Fields in a Worksheet

```sql
SELECT 
    name,
    calculation_logic,
    source_datasource_name
FROM report_attribute
WHERE worksheet_id = 'worksheet-123'
  AND is_calculated = true
ORDER BY name;
```

#### Get Field Lineage Information

```sql
SELECT 
    ra.name AS field_name,
    ra.source_table_name,
    ra.source_column_name,
    ra.lineage_info,
    td.name AS datasource_name,
    td.connection_type
FROM report_attribute ra
LEFT JOIN tableau_datasource td ON ra.datasource_fk_id = td.id
WHERE ra.asset_id = 'field-001';
```

#### Find All Fields from a Specific Data Source

```sql
SELECT 
    name,
    data_type,
    field_role,
    is_calculated,
    source_table_name,
    source_column_name
FROM report_attribute
WHERE source_datasource_id = 'datasource-456'
ORDER BY field_role, name;
```

---

## Table: `tableau_datasource`

The `tableau_datasource` table stores information about **Tableau data sources**, including published data sources, embedded data sources, and custom SQL connections.

### Overview

**Purpose**: Track all data sources used in Tableau, their connection details, upstream tables, and metadata.

**Entity Class**: `com.example.tableau.entity.TableauDataSource`

**Database Table Name**: `tableau_datasource`

### Column Definitions

| Column Name | Data Type | Nullable | Description | Example Value |
|-------------|-----------|----------|-------------|---------------|
| `id` | BIGINT | No | Auto-generated primary key | `1`, `2`, `3` |
| `asset_id` | VARCHAR(128) | No | Unique identifier from Tableau (ID) | `datasource-abc-123` |
| `site_id` | VARCHAR(128) | Yes | ID of the Tableau site | `site-main` |
| `name` | VARCHAR(512) | No | Name of the data source | `Sales Analytics`, `Customer 360` |
| `source_type` | VARCHAR(32) | Yes | Type of data source | `DIRECT_IMPORT`, `CUSTOM_SQL`, `PUBLISHED`, `FILE_BASED`, `OTHER` |
| `description` | TEXT | Yes | Description of the data source | `Primary sales data from PostgreSQL` |
| `owner` | VARCHAR(256) | Yes | Username of the data source owner | `john.doe`, `analytics.team` |
| `owner_id` | VARCHAR(128) | Yes | User ID of the owner | `user-789` |
| `connection_type` | VARCHAR(128) | Yes | Database/connection type | `postgres`, `mysql`, `snowflake`, `excel-direct` |
| `table_name` | VARCHAR(512) | Yes | Table name for direct imports | `fact_sales`, `dim_customer` |
| `schema_name` | VARCHAR(256) | Yes | Database schema name | `public`, `analytics`, `sales_db` |
| `database_name` | VARCHAR(256) | Yes | Database name | `sales_prod`, `analytics_warehouse` |
| `server_name` | VARCHAR(512) | Yes | Database server/host | `db.company.com`, `snowflake.company.com` |
| `custom_sql_query` | TEXT | Yes | SQL query for custom SQL sources | `SELECT * FROM sales WHERE year = 2024` |
| `is_certified` | BOOLEAN | Yes | Whether the data source is certified | `true`, `false` |
| `is_published` | BOOLEAN | Yes | Whether it's a published data source | `true`, `false` |
| `content_url` | VARCHAR(1024) | Yes | URL path for the data source | `/datasources/sales-analytics` |
| `status_flag` | VARCHAR(20) | No | Change tracking status | `NEW`, `UPDATED`, `DELETED`, `ACTIVE` |
| `metadata_hash` | VARCHAR(128) | Yes | SHA-256 hash for change detection | `def789ghi012...` |
| `created_timestamp` | TIMESTAMP | No | When the record was created | `2024-01-10 08:00:00` |
| `last_updated_timestamp` | TIMESTAMP | No | When the record was last updated | `2024-01-22 16:30:00` |
| `workbook_fk_id` | BIGINT | Yes | Foreign key to parent workbook (for embedded sources) | `7` |
| `upstream_tables` | TEXT | Yes | Upstream tables as JSON | `[{"name": "fact_sales", "schema": "public"}]` |
| `calculated_fields` | TEXT | Yes | Calculated fields and formulas as JSON | `[{"name": "Profit Margin", "formula": "..."}]` |

### Unique Constraint

The table has a unique constraint on: `(asset_id, site_id)`

This ensures each data source is unique within a site.

### Source Type Enum Values

| Value | Description | Example Use Case |
|-------|-------------|------------------|
| `DIRECT_IMPORT` | Direct connection to a database table | PostgreSQL table connection |
| `CUSTOM_SQL` | Custom SQL query defining the data | Complex JOIN queries |
| `PUBLISHED` | Published/shared data source | Centrally managed data sources |
| `FILE_BASED` | File-based sources | Excel, CSV files |
| `OTHER` | Other types | Default/unknown types |

### Examples

#### Example 1: Published Data Source (Direct Import)

A certified published data source connected directly to a PostgreSQL table:

```sql
INSERT INTO tableau_datasource VALUES (
    1,                                    -- id
    'datasource-001',                     -- asset_id
    'site-main',                          -- site_id
    'Sales Analytics',                    -- name
    'DIRECT_IMPORT',                      -- source_type
    'Primary sales data from production PostgreSQL database', -- description
    'john.doe',                           -- owner
    'user-123',                           -- owner_id
    'postgres',                           -- connection_type
    'fact_sales',                         -- table_name
    'public',                             -- schema_name
    'sales_prod',                         -- database_name
    'db.company.com',                     -- server_name
    NULL,                                 -- custom_sql_query (not applicable)
    true,                                 -- is_certified
    true,                                 -- is_published
    '/datasources/sales-analytics',       -- content_url
    'ACTIVE',                             -- status_flag
    'hash-abc123...',                     -- metadata_hash
    '2024-01-10 08:00:00',               -- created_timestamp
    '2024-01-10 08:00:00',               -- last_updated_timestamp
    NULL,                                 -- workbook_fk_id (published, not embedded)
    '[{"name": "fact_sales", "schema": "public", "database": "sales_prod"}]', -- upstream_tables
    '[]'                                  -- calculated_fields
);
```

**Use Case**: This is a trusted, published data source that multiple workbooks can use. It's certified by the data governance team and connects directly to the production sales fact table.

#### Example 2: Embedded Data Source with Custom SQL

A custom SQL data source embedded in a workbook:

```sql
INSERT INTO tableau_datasource VALUES (
    2,                                    -- id
    'datasource-002',                     -- asset_id
    'site-main',                          -- site_id
    'Regional Sales Summary',             -- name
    'CUSTOM_SQL',                         -- source_type
    'Aggregated regional sales with customer segments', -- description
    'jane.smith',                         -- owner
    'user-456',                           -- owner_id
    'snowflake',                          -- connection_type
    NULL,                                 -- table_name (custom SQL, not a direct table)
    'analytics',                          -- schema_name
    'sales_warehouse',                    -- database_name
    'snowflake.company.com',              -- server_name
    'SELECT r.region_name, c.segment, SUM(s.sales_amt) as total_sales FROM fact_sales s JOIN dim_region r ON s.region_id = r.id JOIN dim_customer c ON s.customer_id = c.id GROUP BY r.region_name, c.segment', -- custom_sql_query
    false,                                -- is_certified
    false,                                -- is_published (embedded in workbook)
    NULL,                                 -- content_url (embedded sources don't have URLs)
    'ACTIVE',                             -- status_flag
    'hash-def456...',                     -- metadata_hash
    '2024-01-12 10:30:00',               -- created_timestamp
    '2024-01-12 10:30:00',               -- last_updated_timestamp
    5,                                    -- workbook_fk_id (embedded in workbook ID 5)
    '[{"name": "fact_sales", "schema": "analytics"}, {"name": "dim_region", "schema": "analytics"}, {"name": "dim_customer", "schema": "analytics"}]', -- upstream_tables
    '[]'                                  -- calculated_fields
);
```

**Use Case**: This is an embedded data source (specific to one workbook) that uses a custom SQL query to join sales, region, and customer data. It's not published or certified because it's tailored for a specific report.

#### Example 3: Excel File-Based Data Source

A data source connecting to an Excel file:

```sql
INSERT INTO tableau_datasource VALUES (
    3,                                    -- id
    'datasource-003',                     -- asset_id
    'site-main',                          -- site_id
    'Budget Forecast 2024',               -- name
    'FILE_BASED',                         -- source_type
    'Annual budget forecast from Finance team', -- description
    'finance.team',                       -- owner
    'user-789',                           -- owner_id
    'excel-direct',                       -- connection_type
    'Sheet1',                             -- table_name (Excel sheet name)
    NULL,                                 -- schema_name (not applicable for files)
    NULL,                                 -- database_name (not applicable for files)
    NULL,                                 -- server_name (not applicable for files)
    NULL,                                 -- custom_sql_query (not applicable)
    false,                                -- is_certified
    true,                                 -- is_published
    '/datasources/budget-forecast-2024',  -- content_url
    'ACTIVE',                             -- status_flag
    'hash-ghi789...',                     -- metadata_hash
    '2024-01-05 14:00:00',               -- created_timestamp
    '2024-01-05 14:00:00',               -- last_updated_timestamp
    NULL,                                 -- workbook_fk_id (published source)
    '[{"name": "Budget_2024.xlsx", "type": "file"}]', -- upstream_tables
    '[{"name": "Budget Variance", "formula": "[Actual] - [Budget]"}]' -- calculated_fields
);
```

**Use Case**: This represents a published data source based on an Excel file uploaded by the Finance team. It includes a calculated field for budget variance.

#### Example 4: MySQL Connection with Calculated Fields

A direct MySQL connection with calculated fields defined at the data source level:

```sql
INSERT INTO tableau_datasource VALUES (
    4,                                    -- id
    'datasource-004',                     -- asset_id
    'site-main',                          -- site_id
    'Customer Metrics',                   -- name
    'DIRECT_IMPORT',                      -- source_type
    'Customer dimension with calculated KPIs', -- description
    'analytics.team',                     -- owner
    'user-234',                           -- owner_id
    'mysql',                              -- connection_type
    'dim_customer',                       -- table_name
    'crm_db',                             -- schema_name
    'customer_analytics',                 -- database_name
    'mysql.company.com',                  -- server_name
    NULL,                                 -- custom_sql_query
    true,                                 -- is_certified
    true,                                 -- is_published
    '/datasources/customer-metrics',      -- content_url
    'ACTIVE',                             -- status_flag
    'hash-jkl012...',                     -- metadata_hash
    '2024-01-08 09:00:00',               -- created_timestamp
    '2024-01-20 11:30:00',               -- last_updated_timestamp (recently updated)
    NULL,                                 -- workbook_fk_id
    '[{"name": "dim_customer", "schema": "crm_db", "database": "customer_analytics"}]', -- upstream_tables
    '[{"name": "Customer Lifetime Value", "formula": "SUM([Total_Purchases]) / COUNT([Customer_ID])"}, {"name": "Is Active", "formula": "[Last_Purchase_Date] >= DATEADD(month, -6, TODAY())"}]' -- calculated_fields
);
```

**Use Case**: This is a certified published data source from MySQL with several calculated fields defined at the data source level, making them reusable across all worksheets using this source.

### Querying Examples

#### Find All Certified Published Data Sources

```sql
SELECT 
    name,
    description,
    connection_type,
    owner,
    database_name,
    table_name
FROM tableau_datasource
WHERE is_certified = true
  AND is_published = true
  AND status_flag = 'ACTIVE'
ORDER BY name;
```

#### Get Custom SQL Data Sources with Their Queries

```sql
SELECT 
    name,
    connection_type,
    database_name,
    custom_sql_query,
    upstream_tables
FROM tableau_datasource
WHERE source_type = 'CUSTOM_SQL'
  AND status_flag = 'ACTIVE';
```

#### Find Data Sources by Connection Type

```sql
SELECT 
    name,
    source_type,
    database_name,
    server_name,
    table_name,
    is_certified
FROM tableau_datasource
WHERE connection_type = 'postgres'
  AND status_flag != 'DELETED'
ORDER BY is_certified DESC, name;
```

#### Get All Data Sources Used in a Workbook (Embedded)

```sql
SELECT 
    ds.name,
    ds.source_type,
    ds.connection_type,
    ds.database_name,
    ds.table_name,
    wb.name AS workbook_name
FROM tableau_datasource ds
INNER JOIN tableau_workbook wb ON ds.workbook_fk_id = wb.id
WHERE wb.asset_id = 'workbook-123'
  AND ds.status_flag = 'ACTIVE';
```

#### Find Data Sources with Calculated Fields

```sql
SELECT 
    name,
    connection_type,
    calculated_fields
FROM tableau_datasource
WHERE calculated_fields IS NOT NULL
  AND calculated_fields != '[]'
  AND status_flag = 'ACTIVE';
```

---

## Relationships Between Tables

The `report_attribute` and `tableau_datasource` tables are related:

### Foreign Key Relationship

```sql
report_attribute.datasource_fk_id → tableau_datasource.id
```

This relationship indicates which data source a field comes from.

### Example Join Query: Fields with Their Data Source Details

```sql
SELECT 
    ra.name AS field_name,
    ra.field_role,
    ra.is_calculated,
    ra.calculation_logic,
    ra.source_table_name,
    ra.source_column_name,
    ds.name AS datasource_name,
    ds.source_type,
    ds.connection_type,
    ds.database_name,
    ds.server_name
FROM report_attribute ra
LEFT JOIN tableau_datasource ds ON ra.datasource_fk_id = ds.id
WHERE ra.worksheet_id = 'worksheet-123'
  AND ra.status_flag = 'ACTIVE'
ORDER BY ra.field_role, ra.name;
```

This query shows all fields in a worksheet along with their data source connection details.

### Example: Find All Calculated Fields Across Data Sources

```sql
SELECT 
    ds.name AS datasource_name,
    ds.connection_type,
    ra.name AS calculated_field,
    ra.calculation_logic,
    ra.lineage_info
FROM report_attribute ra
INNER JOIN tableau_datasource ds ON ra.datasource_fk_id = ds.id
WHERE ra.is_calculated = true
  AND ra.status_flag = 'ACTIVE'
ORDER BY ds.name, ra.name;
```

This query provides a comprehensive view of all calculated fields, grouped by their data sources.

---

## Change Tracking

Both tables use the same change tracking mechanism:

### Status Flag Values

| Status | Description | When It Occurs |
|--------|-------------|----------------|
| `NEW` | Record found in Tableau but not in database | First ingestion, or new asset created in Tableau |
| `UPDATED` | Existing record with metadata changes | Tracked fields changed (name, description, calculation, etc.) |
| `DELETED` | Record in database no longer exists in Tableau | Asset deleted or removed from Tableau (soft delete) |
| `ACTIVE` | Record synchronized with no changes | Subsequent ingestions with no metadata changes |

### Metadata Hash

Both tables use a `metadata_hash` column (SHA-256) to detect changes efficiently:

- **For report_attribute**: Hash includes `asset_id`, `name`, `worksheet_id`, `datasource_id`, `is_calculated`, `calculation_logic`, `lineage_info`, and `site_id`
- **For tableau_datasource**: Hash varies by type:
  - Published: `asset_id`, `name`, `description`, `is_certified`, `owner`, `connection_type`, `table_name`, `upstream_tables`, `site_id`
  - Embedded: `asset_id`, `name`, `workbook_id`, `connection_type`, `database_name`, table names, `upstream_tables`, `site_id`
  - Custom SQL: `asset_id`, `name`, `custom_sql_query`, `connection_type`, `database_name`, `upstream_tables`, `site_id`

### Example: Find Recently Changed Assets

```sql
-- Find data sources updated in the last 7 days
SELECT 
    name,
    source_type,
    status_flag,
    last_updated_timestamp
FROM tableau_datasource
WHERE status_flag = 'UPDATED'
  AND last_updated_timestamp > CURRENT_TIMESTAMP - INTERVAL '7 days'
ORDER BY last_updated_timestamp DESC;

-- Find report attributes (fields) updated in the last 7 days
SELECT 
    ra.name,
    ra.worksheet_id,
    ra.status_flag,
    ra.last_updated_timestamp,
    ds.name AS datasource_name
FROM report_attribute ra
LEFT JOIN tableau_datasource ds ON ra.datasource_fk_id = ds.id
WHERE ra.status_flag = 'UPDATED'
  AND ra.last_updated_timestamp > CURRENT_TIMESTAMP - INTERVAL '7 days'
ORDER BY ra.last_updated_timestamp DESC;
```

---

## Practical Use Cases

### Use Case 1: Data Lineage Analysis

**Goal**: Trace which database tables and columns feed into a specific report field.

```sql
SELECT 
    ra.name AS field_name,
    ra.source_table_name,
    ra.source_column_name,
    ra.calculation_logic,
    ds.name AS datasource_name,
    ds.connection_type,
    ds.database_name,
    ds.schema_name,
    ds.server_name,
    ds.upstream_tables
FROM report_attribute ra
INNER JOIN tableau_datasource ds ON ra.datasource_fk_id = ds.id
WHERE ra.asset_id = 'field-abc-123';
```

### Use Case 2: Impact Analysis for Database Changes

**Goal**: Find all Tableau fields affected if we rename a database column `sales_amt` to `sales_amount`.

```sql
SELECT 
    ra.name AS affected_field,
    ra.worksheet_id,
    ra.source_datasource_name,
    ra.source_table_name,
    ra.source_column_name,
    ra.calculation_logic
FROM report_attribute ra
WHERE ra.source_column_name = 'sales_amt'
   OR ra.calculation_logic LIKE '%sales_amt%'
   OR ra.lineage_info LIKE '%sales_amt%';
```

### Use Case 3: Audit Certified Data Sources

**Goal**: List all certified data sources and their usage in worksheets.

```sql
SELECT 
    ds.name AS datasource_name,
    ds.description,
    ds.owner,
    ds.connection_type,
    ds.database_name,
    COUNT(DISTINCT ra.worksheet_id) AS worksheet_count,
    COUNT(ra.id) AS field_count
FROM tableau_datasource ds
LEFT JOIN report_attribute ra ON ds.id = ra.datasource_fk_id
WHERE ds.is_certified = true
  AND ds.status_flag = 'ACTIVE'
GROUP BY ds.id, ds.name, ds.description, ds.owner, ds.connection_type, ds.database_name
ORDER BY worksheet_count DESC;
```

### Use Case 4: Find Orphaned or Unused Data Sources

**Goal**: Identify data sources that are not being used by any report attributes.

```sql
SELECT 
    ds.name,
    ds.source_type,
    ds.is_published,
    ds.owner,
    ds.created_timestamp
FROM tableau_datasource ds
LEFT JOIN report_attribute ra ON ds.id = ra.datasource_fk_id
WHERE ra.id IS NULL
  AND ds.status_flag = 'ACTIVE'
ORDER BY ds.created_timestamp DESC;
```

### Use Case 5: Calculate Complexity Metrics

**Goal**: Find the most complex calculated fields (by formula length).

```sql
SELECT 
    ra.name AS field_name,
    ra.source_datasource_name,
    LENGTH(ra.calculation_logic) AS formula_length,
    ra.calculation_logic,
    ra.worksheet_id
FROM report_attribute ra
WHERE ra.is_calculated = true
  AND ra.status_flag = 'ACTIVE'
ORDER BY formula_length DESC
LIMIT 10;
```

---

## Related Documentation

- **[README.md](README.md)**: General application overview and setup
- **[TRACKED_FIELDS.md](TRACKED_FIELDS.md)**: Details on which fields trigger change detection
- **[IDENTIFIER_USAGE.md](IDENTIFIER_USAGE.md)**: Information about ID vs LUID usage
- **[UPDATED_STATUS_EXPLAINED.md](UPDATED_STATUS_EXPLAINED.md)**: How the UPDATED status works

---

## Additional Notes

### Timestamps

Both tables automatically manage timestamps:
- `created_timestamp`: Set automatically when a record is first created (`@PrePersist`)
- `last_updated_timestamp`: Updated automatically on every modification (`@PreUpdate`)

### Data Persistence

The application supports two database backends:
- **H2** (default): File-based persistence for development (files in `./data/`)
- **MariaDB**: Production-ready relational database

### GraphQL API Source

Data is extracted from Tableau's Metadata API (GraphQL):
- **Report Attributes**: `sheetFieldInstances` query with upstream field lineage
- **Data Sources**: `publishedDatasources` and embedded data source queries

For more details on GraphQL queries, see `TableauGraphQLClient.java` (lines 128-562).

---

*Last Updated: 2026-01-27*
