-- Flyway migration to add lineage edges, calculation rules, metadata_hash and audit
CREATE TABLE IF NOT EXISTS lineage_edge (
  id bigserial PRIMARY KEY,
  from_name varchar(512) NOT NULL,
  from_id varchar(128) NOT NULL,
  to_name varchar(512) NOT NULL,
  to_id varchar(128) NOT NULL,
  relation_type varchar(64),
  metadata jsonb,
  created_at timestamptz DEFAULT now()
);

CREATE TABLE IF NOT EXISTS calculation_rule (
  id bigserial PRIMARY KEY,
  report_attr_name varchar(512) NOT NULL,
  report_attr_id varchar(128) NOT NULL,
  expression_text text,
  parsed_expression jsonb,
  is_custom_sql boolean DEFAULT false,
  metadata jsonb,
  created_at timestamptz DEFAULT now(),
  last_updated timestamptz
);

-- Add metadata_hash and ingested_at columns to asset tables
ALTER TABLE IF EXISTS tableau_site ADD COLUMN IF NOT EXISTS metadata_hash varchar(128);
ALTER TABLE IF EXISTS tableau_project ADD COLUMN IF NOT EXISTS metadata_hash varchar(128);
ALTER TABLE IF EXISTS tableau_workbook ADD COLUMN IF NOT EXISTS metadata_hash varchar(128);
ALTER TABLE IF EXISTS tableau_worksheet ADD COLUMN IF NOT EXISTS metadata_hash varchar(128);
ALTER TABLE IF EXISTS report_attribute ADD COLUMN IF NOT EXISTS metadata_hash varchar(128);
ALTER TABLE IF EXISTS datasource ADD COLUMN IF NOT EXISTS metadata_hash varchar(128);

ALTER TABLE IF EXISTS tableau_site ADD COLUMN IF NOT EXISTS ingested_at timestamptz;
ALTER TABLE IF EXISTS tableau_project ADD COLUMN IF NOT EXISTS ingested_at timestamptz;
ALTER TABLE IF EXISTS tableau_workbook ADD COLUMN IF NOT EXISTS ingested_at timestamptz;
ALTER TABLE IF EXISTS tableau_worksheet ADD COLUMN IF NOT EXISTS ingested_at timestamptz;
ALTER TABLE IF EXISTS report_attribute ADD COLUMN IF NOT EXISTS ingested_at timestamptz;
ALTER TABLE IF EXISTS datasource ADD COLUMN IF NOT EXISTS ingested_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_to_ingest_site ON tableau_site (to_ingest);
CREATE INDEX IF NOT EXISTS idx_to_ingest_report_attr ON report_attribute (to_ingest);
CREATE INDEX IF NOT EXISTS idx_metadata_hash_site ON tableau_site (metadata_hash);