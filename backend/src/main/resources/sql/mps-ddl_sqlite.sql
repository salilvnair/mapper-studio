PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS mps_mapping_project (
  project_code TEXT PRIMARY KEY,
  project_name TEXT NOT NULL,
  source_type TEXT NOT NULL,
  description TEXT,
  created_by TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mps_mapping_version (
  version_id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_code TEXT NOT NULL,
  version_code TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
  target_schema_json TEXT NOT NULL,
  artifact_id TEXT,
  created_by TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TEXT,
  UNIQUE(project_code, version_code),
  FOREIGN KEY(project_code) REFERENCES mps_mapping_project(project_code)
);

CREATE TABLE IF NOT EXISTS mps_mapping_field (
  mapping_id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_code TEXT NOT NULL,
  version_code TEXT NOT NULL,
  source_path TEXT NOT NULL,
  target_path TEXT NOT NULL,
  transform_type TEXT NOT NULL,
  transform_config TEXT,
  confidence REAL,
  reasoning TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mps_mapping_field_proj_ver ON mps_mapping_field(project_code, version_code);

CREATE TABLE IF NOT EXISTS mps_mapping_validation (
  validation_id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_code TEXT NOT NULL,
  version_code TEXT NOT NULL,
  missing_required TEXT NOT NULL DEFAULT '[]',
  type_mismatch TEXT NOT NULL DEFAULT '[]',
  duplicate_mappings TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mps_mapping_publish_audit (
  publish_audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_code TEXT NOT NULL,
  version_code TEXT NOT NULL,
  artifact_id TEXT NOT NULL,
  published_by TEXT NOT NULL,
  published_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notes TEXT
);

CREATE TABLE IF NOT EXISTS mps_mapping_manual_confirm_audit (
  confirm_audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_code TEXT NOT NULL,
  version_code TEXT NOT NULL,
  confirmed INTEGER NOT NULL DEFAULT 1,
  confirmed_by TEXT NOT NULL,
  selected_count INTEGER NOT NULL DEFAULT 0,
  mapping_snapshot TEXT NOT NULL DEFAULT '[]',
  notes TEXT,
  confirmed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mps_mapping_manual_confirm_proj_ver
  ON mps_mapping_manual_confirm_audit(project_code, version_code, confirmed_at DESC);
