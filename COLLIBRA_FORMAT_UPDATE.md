# Collibra JSON Format Update - Summary

## Overview
This update modifies the JSON format for Collibra asset import to match the standardized format specified in the problem statement. All Tableau asset types now export in a consistent, structured format that aligns with Collibra's meta model requirements.

## Changes Made

### 1. New DTO Classes
Created new supporting DTO classes for the nested JSON structure:
- **`CollibraType`** - Represents the asset type with a `name` field
- **`CollibraCommunity`** - Represents the community with a `name` field
- **`CollibraDomain`** - Represents the domain with `name` and `community` fields
- **`CollibraIdentifier`** - Represents the unique identifier with `name` and `domain` fields
- **`CollibraAttributeValue`** - Represents a single attribute value
- **`CollibraRelationTarget`** - Represents a relation target with `name` and `domain` fields

### 2. Updated `CollibraAsset` DTO
The main `CollibraAsset` class now uses the new format:

**Old Format:**
```java
@Data
public class CollibraAsset {
    private String externalEntityId;
    private String fullName;
    private String displayName;
    private String assetTypeName;
    private String domainName;
    private String communityName;
    private String status;
    private List<CollibraAttribute> attributes;
    private List<CollibraRelation> sourceRelations;
    private List<CollibraRelation> targetRelations;
}
```

**New Format:**
```java
@Data
public class CollibraAsset {
    @Builder.Default
    private String resourceType = "Asset";
    private CollibraType type;
    private String displayName;
    private CollibraIdentifier identifier;
    private Map<String, List<CollibraAttributeValue>> attributes;
    private Map<String, List<CollibraRelationTarget>> relations;
}
```

### 3. Updated Service Mapping Methods
All mapping methods in `CollibraIngestionService` have been updated:
- `mapServerToCollibraAsset()` - Maps Tableau Server
- `mapSiteToCollibraAsset()` - Maps Tableau Site
- `mapProjectToCollibraAsset()` - Maps Tableau Project (including nested projects)
- `mapWorkbookToCollibraAsset()` - Maps Tableau Workbook
- `mapWorksheetToCollibraAsset()` - Maps Tableau Worksheet
- `mapDataSourceToCollibraAsset()` - Maps Tableau Data Source
- `mapReportAttributeToCollibraAsset()` - Maps Tableau Report Attribute

### 4. JSON Format Example

**Server Example:**
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

**Site Example with Relation:**
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
    "Description": [
      {
        "value": "Tableau Site: Marketing Site"
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

## Backward Compatibility

The old `CollibraAttribute` and `CollibraRelation` classes have been deprecated but remain in the codebase for backward compatibility. They are marked with `@Deprecated` annotations.

## Documentation

A comprehensive documentation file `COLLIBRA_JSON_SAMPLES.md` has been created with:
- Complete JSON samples for all 7 asset types
- Attribute definitions for each asset type
- Relation definitions showing parent-child relationships
- Asset hierarchy diagram
- Configuration properties guide

## Testing

All existing tests pass successfully:
- ✅ 11/11 tests in `CollibraIngestionServiceTest` passing
- ✅ Build compiles successfully without errors
- ✅ JSON serialization verified

## Asset Types Covered

1. **Tableau Server** - Root of the hierarchy
2. **Tableau Site** - Child of Server
3. **Tableau Project** - Child of Site (supports nesting)
4. **Tableau Workbook** - Child of Project
5. **Tableau Worksheet** - Child of Workbook
6. **Tableau Data Source** - Can be standalone or child of Workbook
7. **Tableau Report Attribute** - Child of Worksheet, references Data Source

## Relation Types

- **relationid:SOURCE** - Represents parent-child containment relationships
- **relationid:TARGET** - Represents lineage/dependency relationships (e.g., Report Attribute sources from Data Source)

## Identifier Format

All assets use the format: `{assetId} > {assetName}`

Examples:
- `srv-001 > Production Tableau Server`
- `site-123abc > Marketing Site`
- `proj-456def > Sales Analytics`

## Key Benefits

1. **Consistent Structure** - All assets follow the same format pattern
2. **Clear Hierarchy** - Nested domain/community structure matches Collibra's model
3. **Rich Metadata** - Comprehensive attributes for each asset type
4. **Lineage Support** - Relations capture both containment and data lineage
5. **Configurable** - Domain names customizable via application properties

## Configuration

Configure domain names in `application.properties`:

```properties
collibra.community-name=Tableau Technology
collibra.domain.server=Tableau Servers
collibra.domain.site=Tableau Sites
collibra.domain.project=Tableau Projects
collibra.domain.workbook=Tableau Workbooks
collibra.domain.worksheet=Tableau Worksheets
collibra.domain.datasource=Tableau Data Sources
collibra.domain.report-attribute=Tableau Report Attributes
```

## Next Steps

To use this format in Collibra:

1. Review the `COLLIBRA_JSON_SAMPLES.md` file for complete samples
2. Configure your Collibra meta model with the asset types, attributes, and relations listed
3. Update your application.properties with appropriate domain names
4. Run the ingestion endpoints to export Tableau metadata to Collibra

## API Endpoints

The following endpoints export data in the new format:

- `POST /api/collibra/ingest/servers`
- `POST /api/collibra/ingest/sites`
- `POST /api/collibra/ingest/projects`
- `POST /api/collibra/ingest/workbooks`
- `POST /api/collibra/ingest/worksheets`
- `POST /api/collibra/ingest/datasources`
- `POST /api/collibra/ingest/report-attributes`
- `POST /api/collibra/ingest/all` - Ingests all asset types in order
