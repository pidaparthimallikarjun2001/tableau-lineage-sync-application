# Collibra Import JSON Format - Sample for All Asset Types

This document provides sample JSON formats for importing all Tableau asset types into Collibra. Use these samples to configure your Collibra meta model with the appropriate attributes and relations.

## Important: JSON File Format

The Collibra Import API expects the JSON file to be an **array of resource objects** at the root level:

```json
[
  {
    "resourceType": "Asset",
    "type": { "name": "Tableau Server" },
    ...
  },
  {
    "resourceType": "Asset",
    "type": { "name": "Tableau Site" },
    ...
  }
]
```

**Do NOT wrap** the assets in an object with an "assets" property. The multipart form parameters (`sendNotification`, `continueOnError`) should be sent as separate form fields, not inside the JSON file.

## Table of Contents
1. [Tableau Server](#1-tableau-server)
2. [Tableau Site](#2-tableau-site)
3. [Tableau Project](#3-tableau-project)
4. [Tableau Workbook](#4-tableau-workbook)
5. [Tableau Worksheet](#5-tableau-worksheet)
6. [Tableau Data Source](#6-tableau-data-source)
7. [Tableau Report Attribute](#7-tableau-report-attribute)

---

## 1. Tableau Server

### Asset Type
**Name:** `Tableau Server`

### Domain
**Name:** Configurable via `collibra.domain.server` (default: "Tableau Servers")  
**Community:** Configurable via `collibra.community-name` (default: "Tableau Technology")

### Sample JSON
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Server"
  },
  "displayName": "Production Tableau Server",
  "identifier": {
    "name": "srv-001 > Production Tableau Server",
    "domain": {
      "name": "Tableau Servers",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Description": [
      {
        "value": "Tableau Server: Production Tableau Server"
      }
    ],
    "URL": [
      {
        "value": "https://tableau.company.com"
      }
    ],
    "Version": [
      {
        "value": "2023.3.0"
      }
    ]
  }
}
```

### Attributes to Configure in Collibra
- **Description** (String) - Description of the Tableau Server
- **URL** (String) - URL of the Tableau Server
- **Version** (String) - Tableau Server version

### Relations
None (Server is the root of the hierarchy)

---

## 2. Tableau Site

### Asset Type
**Name:** `Tableau Site`

### Domain
**Name:** Configurable via `collibra.domain.site` (default: "Tableau Sites")  
**Community:** Configurable via `collibra.community-name` (default: "Tableau Technology")

### Sample JSON
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Site"
  },
  "displayName": "Marketing Site",
  "identifier": {
    "name": "site-123abc > Marketing Site",
    "domain": {
      "name": "Tableau Sites",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "URL": [
      {
        "value": "https://tableau.company.com/#/site/marketing"
      }
    ]
  },
  "relations": {
    "relationid:SOURCE": [
      {
        "domain": {
          "name": "Tableau Servers",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "srv-001 > Production Tableau Server"
      }
    ]
  }
}
```

### Attributes to Configure in Collibra
- **URL** (String) - Full URL to access the site in format: `{serverUrl}/#/site/{contentUrl}`

### Relations to Configure in Collibra
- **relationid:SOURCE** - Points to parent Tableau Server (contains relationship)

---

## 3. Tableau Project

### Asset Type
**Name:** `Tableau Project`

### Domain
**Name:** Configurable via `collibra.domain.project` (default: "Tableau Projects")  
**Community:** Configurable via `collibra.community-name` (default: "Tableau Technology")

### Sample JSON
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Project"
  },
  "displayName": "Sales Analytics",
  "identifier": {
    "name": "site-123abc > proj-456def > Sales Analytics",
    "domain": {
      "name": "Tableau Projects",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Description": [
      {
        "value": "Sales analytics dashboards and reports"
      }
    ],
    "Owner in Source": [
      {
        "value": "john.doe@company.com"
      }
    ]
  },
  "relations": {
    "relationid:SOURCE": [
      {
        "domain": {
          "name": "Tableau Sites",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "site-123abc > Marketing Site"
      }
    ]
  }
}
```

### Sample JSON (Nested Project)
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Project"
  },
  "displayName": "Q1 Reports",
  "identifier": {
    "name": "site-123abc > proj-789ghi > Q1 Reports",
    "domain": {
      "name": "Tableau Projects",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Description": [
      {
        "value": "Q1 2024 sales reports"
      }
    ],
    "Owner in Source": [
      {
        "value": "jane.smith@company.com"
      }
    ]
  },
  "relations": {
    "relationid:SOURCE": [
      {
        "domain": {
          "name": "Tableau Projects",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "site-123abc > proj-456def > Sales Analytics"
      },
      {
        "domain": {
          "name": "Tableau Sites",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "site-123abc > Marketing Site"
      }
    ]
  }
}
```

### Attributes to Configure in Collibra
- **Description** (String) - Project description
- **Owner in Source** (String) - Owner username from Tableau

### Relations to Configure in Collibra
- **relationid:SOURCE** - Points to parent Site and optionally parent Project (for nested projects)

---

## 4. Tableau Workbook

### Asset Type
**Name:** `Tableau Workbook`

### Domain
**Name:** Configurable via `collibra.domain.workbook` (default: "Tableau Workbooks")  
**Community:** Configurable via `collibra.community-name` (default: "Tableau Technology")

### Sample JSON
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Workbook"
  },
  "displayName": "Sales Dashboard",
  "identifier": {
    "name": "wb-111jkl > Sales Dashboard",
    "domain": {
      "name": "Tableau Workbooks",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Description": [
      {
        "value": "Comprehensive sales performance dashboard"
      }
    ],
    "Owner": [
      {
        "value": "john.doe@company.com"
      }
    ],
    "Content URL": [
      {
        "value": "SalesDashboard/sheets/Overview"
      }
    ],
    "Site ID": [
      {
        "value": "site-123abc"
      }
    ]
  },
  "relations": {
    "relationid:SOURCE": [
      {
        "domain": {
          "name": "Tableau Projects",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "site-123abc > proj-456def > Sales Analytics"
      }
    ]
  }
}
```

### Attributes to Configure in Collibra
- **Description** (String) - Workbook description
- **Owner** (String) - Owner username/email
- **Content URL** (String) - Content URL path for the workbook
- **Site ID** (String) - Site identifier for composite uniqueness

### Relations to Configure in Collibra
- **relationid:SOURCE** - Points to parent Project

---

## 5. Tableau Worksheet

### Asset Type
**Name:** `Tableau Worksheet`

### Domain
**Name:** Configurable via `collibra.domain.worksheet` (default: "Tableau Worksheets")  
**Community:** Configurable via `collibra.community-name` (default: "Tableau Technology")

### Sample JSON
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Worksheet"
  },
  "displayName": "Sales Overview",
  "identifier": {
    "name": "ws-222mno > Sales Overview",
    "domain": {
      "name": "Tableau Worksheets",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Owner": [
      {
        "value": "john.doe@company.com"
      }
    ],
    "Site ID": [
      {
        "value": "site-123abc"
      }
    ]
  },
  "relations": {
    "relationid:SOURCE": [
      {
        "domain": {
          "name": "Tableau Workbooks",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "wb-111jkl > Sales Dashboard"
      }
    ]
  }
}
```

### Attributes to Configure in Collibra
- **Owner** (String) - Owner username/email
- **Site ID** (String) - Site identifier for composite uniqueness

### Relations to Configure in Collibra
- **relationid:SOURCE** - Points to parent Workbook

---

## 6. Tableau Data Source

### Asset Type
**Name:** `Tableau Data Source`

### Domain
**Name:** Configurable via `collibra.domain.datasource` (default: "Tableau Data Sources")  
**Community:** Configurable via `collibra.community-name` (default: "Tableau Technology")

### Sample JSON (Published Data Source)
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Data Source"
  },
  "displayName": "Sales Database",
  "identifier": {
    "name": "ds-333pqr > Sales Database",
    "domain": {
      "name": "Tableau Data Sources",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Description": [
      {
        "value": "Main sales database connection"
      }
    ],
    "Owner": [
      {
        "value": "data.admin@company.com"
      }
    ],
    "Connection Type": [
      {
        "value": "postgres"
      }
    ],
    "Table Name": [
      {
        "value": "sales_fact"
      }
    ],
    "Schema Name": [
      {
        "value": "public"
      }
    ],
    "Database Name": [
      {
        "value": "sales_db"
      }
    ],
    "Server Name": [
      {
        "value": "db-server-01.company.com"
      }
    ],
    "Is Certified": [
      {
        "value": "true"
      }
    ],
    "Is Published": [
      {
        "value": "true"
      }
    ],
    "Source Type": [
      {
        "value": "DIRECT_IMPORT"
      }
    ],
    "Site ID": [
      {
        "value": "site-123abc"
      }
    ]
  }
}
```

### Sample JSON (Embedded Data Source with Custom SQL)
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Data Source"
  },
  "displayName": "Custom Sales Query",
  "identifier": {
    "name": "ds-444stu > Custom Sales Query",
    "domain": {
      "name": "Tableau Data Sources",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Description": [
      {
        "value": "Custom query for sales analysis"
      }
    ],
    "Owner": [
      {
        "value": "john.doe@company.com"
      }
    ],
    "Connection Type": [
      {
        "value": "snowflake"
      }
    ],
    "Database Name": [
      {
        "value": "ANALYTICS_DB"
      }
    ],
    "Server Name": [
      {
        "value": "company.snowflakecomputing.com"
      }
    ],
    "Is Certified": [
      {
        "value": "false"
      }
    ],
    "Is Published": [
      {
        "value": "false"
      }
    ],
    "Source Type": [
      {
        "value": "CUSTOM_SQL"
      }
    ],
    "Site ID": [
      {
        "value": "site-123abc"
      }
    ]
  },
  "relations": {
    "relationid:SOURCE": [
      {
        "domain": {
          "name": "Tableau Workbooks",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "wb-111jkl > Sales Dashboard"
      }
    ]
  }
}
```

### Attributes to Configure in Collibra
- **Description** (String) - Data source description
- **Owner** (String) - Owner username/email
- **Connection Type** (String) - Type of database connection (e.g., postgres, mysql, snowflake, oracle)
- **Table Name** (String) - Name of the source table (for direct imports)
- **Schema Name** (String) - Database schema name
- **Database Name** (String) - Database name
- **Server Name** (String) - Server/host name
- **Is Certified** (Boolean String) - Whether the data source is certified
- **Is Published** (Boolean String) - Whether this is a published data source
- **Source Type** (String) - Type of source (DIRECT_IMPORT, CUSTOM_SQL, PUBLISHED, FILE_BASED, OTHER)
- **Site ID** (String) - Site identifier for composite uniqueness

### Relations to Configure in Collibra
- **relationid:SOURCE** - Points to parent Workbook (for embedded data sources only)

---

## 7. Tableau Report Attribute

### Asset Type
**Name:** `Tableau Report Attribute`

### Domain
**Name:** Configurable via `collibra.domain.report-attribute` (default: "Tableau Report Attributes")  
**Community:** Configurable via `collibra.community-name` (default: "Tableau Technology")

### Sample JSON (Calculated Field)
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Report Attribute"
  },
  "displayName": "Total Revenue",
  "identifier": {
    "name": "ra-555vwx > Total Revenue",
    "domain": {
      "name": "Tableau Report Attributes",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Technical Data Type": [
      {
        "value": "REAL"
      }
    ],
    "Role in Report": [
      {
        "value": "measure"
      }
    ],
    "Is Calculated": [
      {
        "value": "true"
      }
    ],
    "Calculation Rule": [
      {
        "value": "SUM([Sales Amount]) - SUM([Discounts])"
      }
    ],
    "Source DataSource ID": [
      {
        "value": "ds-333pqr"
      }
    ],
    "Source DataSource Name": [
      {
        "value": "Sales Database"
      }
    ],
    "Site ID": [
      {
        "value": "site-123abc"
      }
    ],
    "Worksheet ID": [
      {
        "value": "ws-222mno"
      }
    ]
  },
  "relations": {
    "0195fd1e-47f7-7674-96eb-e91ff0ce71c4:SOURCE": [
      {
        "domain": {
          "name": "Tableau Worksheets",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "ws-222mno > Sales Overview"
      }
    ]
  }
}
```

### Sample JSON (Direct Field)
```json
{
  "resourceType": "Asset",
  "type": {
    "name": "Tableau Report Attribute"
  },
  "displayName": "Customer Name",
  "identifier": {
    "name": "ra-666yza > Customer Name",
    "domain": {
      "name": "Tableau Report Attributes",
      "community": {
        "name": "Tableau Technology"
      }
    }
  },
  "attributes": {
    "Technical Data Type": [
      {
        "value": "STRING"
      }
    ],
    "Role in Report": [
      {
        "value": "dimension"
      }
    ],
    "Is Calculated": [
      {
        "value": "false"
      }
    ],
    "Source DataSource ID": [
      {
        "value": "ds-333pqr"
      }
    ],
    "Source DataSource Name": [
      {
        "value": "Sales Database"
      }
    ],
    "Source Column Name": [
      {
        "value": "customer_name"
      }
    ],
    "Source Table Name": [
      {
        "value": "customers"
      }
    ],
    "Site ID": [
      {
        "value": "site-123abc"
      }
    ],
    "Worksheet ID": [
      {
        "value": "ws-222mno"
      }
    ]
  },
  "relations": {
    "0195fd1e-47f7-7674-96eb-e91ff0ce71c4:SOURCE": [
      {
        "domain": {
          "name": "Tableau Worksheets",
          "community": {
            "name": "Tableau Technology"
          }
        },
        "name": "ws-222mno > Sales Overview"
      }
    ]
  }
}
```

### Attributes to Configure in Collibra
- **Technical Data Type** (String) - Field data type (STRING, INTEGER, REAL, BOOLEAN, DATE, DATETIME, etc.)
- **Role in Report** (String) - Role of the field (dimension, measure, etc.)
- **Is Calculated** (Boolean String) - Whether this is a calculated field
- **Calculation Rule** (String) - Formula/calculation for calculated fields
- **Source DataSource ID** (String) - ID of the source data source
- **Source DataSource Name** (String) - Name of the source data source
- **Source Column Name** (String) - Name of the source column (for direct fields)
- **Source Table Name** (String) - Name of the source table
- **Site ID** (String) - Site identifier for composite uniqueness
- **Worksheet ID** (String) - Worksheet identifier for composite uniqueness

### Relations to Configure in Collibra
- **0195fd1e-47f7-7674-96eb-e91ff0ce71c4:SOURCE** - Points to parent Worksheet (Tableau Worksheet contains Tableau Report Attribute)

---

## Asset Hierarchy

```
Tableau Server (root)
  └── Tableau Site
       └── Tableau Project
            ├── Tableau Project (nested, optional)
            └── Tableau Workbook
                 ├── Tableau Worksheet
                 │    └── Tableau Report Attribute
                 └── Tableau Data Source (embedded)

Tableau Data Source (published, standalone)
```

---

## Configuration Properties

Configure these properties in your `application.properties` to customize domain names:

```properties
# Collibra Configuration
collibra.community-name=Tableau Technology
collibra.domain.server=Tableau Servers
collibra.domain.site=Tableau Sites
collibra.domain.project=Tableau Projects
collibra.domain.workbook=Tableau Workbooks
collibra.domain.worksheet=Tableau Worksheets
collibra.domain.datasource=Tableau Data Sources
collibra.domain.report-attribute=Tableau Report Attributes
```

---

## Notes

1. **Identifier Format**: All identifiers follow the format `{assetId} > {assetName}` to ensure uniqueness
2. **Relations**: 
   - `relationid:SOURCE` represents parent-child relationships (e.g., Workbook contains Worksheet)
   - Specific UUID relations are used for certain relationships (e.g., `0195fd1e-47f7-7674-96eb-e91ff0ce71c4:SOURCE` for Worksheet-to-ReportAttribute)
3. **Composite Uniqueness**: Site ID is included as an attribute for assets that can exist across multiple sites
4. **Null Values**: Attributes with null or empty values are excluded from the JSON
5. **Relations**: Relations with no parent are excluded from the JSON (e.g., Server has no parent)
