CREATE TABLE IF NOT EXISTS mps_mapping_project (
  project_code text PRIMARY KEY,
  project_name text NOT NULL,
  source_type text NOT NULL,
  description text,
  created_by text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mps_mapping_version (
  version_id bigserial PRIMARY KEY,
  project_code text NOT NULL REFERENCES mps_mapping_project(project_code),
  version_code text NOT NULL,
  status text NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
  target_schema_json jsonb NOT NULL,
  artifact_id text,
  created_by text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz,
  UNIQUE(project_code, version_code)
);

CREATE TABLE IF NOT EXISTS mps_mapping_field (
  mapping_id bigserial PRIMARY KEY,
  project_code text NOT NULL,
  version_code text NOT NULL,
  source_path text NOT NULL,
  target_path text NOT NULL,
  transform_type text NOT NULL,
  transform_config jsonb,
  confidence numeric(5,4),
  reasoning text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mps_mapping_field_proj_ver ON mps_mapping_field(project_code, version_code);

CREATE TABLE IF NOT EXISTS mps_mapping_validation (
  validation_id bigserial PRIMARY KEY,
  project_code text NOT NULL,
  version_code text NOT NULL,
  missing_required jsonb NOT NULL DEFAULT '[]'::jsonb,
  type_mismatch jsonb NOT NULL DEFAULT '[]'::jsonb,
  duplicate_mappings jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mps_mapping_publish_audit (
  publish_audit_id bigserial PRIMARY KEY,
  project_code text NOT NULL,
  version_code text NOT NULL,
  artifact_id text NOT NULL,
  published_by text NOT NULL,
  published_at timestamptz NOT NULL DEFAULT now(),
  notes text
);

CREATE TABLE IF NOT EXISTS mps_mapping_manual_confirm_audit (
  confirm_audit_id bigserial PRIMARY KEY,
  project_code text NOT NULL,
  version_code text NOT NULL,
  confirmed boolean NOT NULL DEFAULT true,
  confirmed_by text NOT NULL,
  selected_count integer NOT NULL DEFAULT 0,
  mapping_snapshot jsonb NOT NULL DEFAULT '[]'::jsonb,
  notes text,
  confirmed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mps_mapping_manual_confirm_proj_ver
  ON mps_mapping_manual_confirm_audit(project_code, version_code, confirmed_at desc);
