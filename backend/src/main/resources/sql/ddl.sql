-- ==================== CE TABLES ====================

CREATE TABLE IF NOT EXISTS ce_config (
  config_id integer PRIMARY KEY,
  config_type text NOT NULL,
  config_key text NOT NULL,
  config_value text NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ce_config_type_key ON ce_config (config_type, config_key);

CREATE TABLE IF NOT EXISTS ce_intent (
  intent_code text PRIMARY KEY,
  description text NOT NULL,
  priority integer NOT NULL DEFAULT 100,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  display_name text,
  llm_hint text
);
CREATE INDEX IF NOT EXISTS ix_ce_intent_enabled_priority ON ce_intent (enabled, priority, intent_code);

CREATE TABLE IF NOT EXISTS ce_intent_classifier (
  classifier_id bigserial PRIMARY KEY,
  intent_code text NOT NULL,
  rule_type text NOT NULL,
  pattern text NOT NULL,
  priority integer NOT NULL,
  enabled boolean DEFAULT true,
  description text
);

CREATE TABLE IF NOT EXISTS ce_output_schema (
  schema_id bigserial PRIMARY KEY,
  intent_code text NOT NULL,
  state_code text NOT NULL,
  json_schema jsonb NOT NULL,
  description text,
  enabled boolean DEFAULT true,
  priority integer NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ce_output_schema_lookup ON ce_output_schema (intent_code, state_code, enabled, priority);

CREATE TABLE IF NOT EXISTS ce_prompt_template (
  template_id bigserial PRIMARY KEY,
  intent_code text,
  state_code text,
  response_type text NOT NULL,
  system_prompt text NOT NULL,
  user_prompt text NOT NULL,
  temperature numeric(3, 2) NOT NULL DEFAULT 0.0,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_prompt_template_lookup ON ce_prompt_template (response_type, intent_code, state_code, enabled);

CREATE TABLE IF NOT EXISTS ce_response (
  response_id bigserial PRIMARY KEY,
  intent_code text,
  state_code text NOT NULL,
  output_format text NOT NULL,
  response_type text NOT NULL,
  exact_text text,
  derivation_hint text,
  json_schema jsonb,
  priority integer NOT NULL DEFAULT 100,
  enabled boolean NOT NULL DEFAULT true,
  description text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_response_intent_state ON ce_response (intent_code, state_code, enabled, priority);
CREATE INDEX IF NOT EXISTS idx_ce_response_lookup ON ce_response (state_code, enabled, priority);

CREATE TABLE IF NOT EXISTS ce_rule (
  rule_id bigserial PRIMARY KEY,
  phase text NOT NULL DEFAULT 'PIPELINE_RULES',
  intent_code text,
  state_code text,
  rule_type text NOT NULL,
  match_pattern text NOT NULL,
  action text NOT NULL,
  action_value text,
  priority integer NOT NULL DEFAULT 100,
  enabled boolean NOT NULL DEFAULT true,
  description text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_rule_priority ON ce_rule (enabled, phase, state_code, priority);

CREATE TABLE IF NOT EXISTS ce_policy (
  policy_id bigserial PRIMARY KEY,
  rule_type text NOT NULL,
  pattern text NOT NULL,
  response_text text NOT NULL,
  priority integer NOT NULL DEFAULT 10,
  enabled boolean NOT NULL DEFAULT true,
  description text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_policy_priority ON ce_policy (enabled, priority);

CREATE TABLE IF NOT EXISTS ce_conversation (
  conversation_id uuid PRIMARY KEY,
  status text NOT NULL,
  intent_code text,
  state_code text NOT NULL,
  context_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  last_user_text text,
  last_assistant_json jsonb,
  input_params_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_conversation_status ON ce_conversation (status);
CREATE INDEX IF NOT EXISTS idx_ce_conversation_updated ON ce_conversation (updated_at);

CREATE TABLE IF NOT EXISTS ce_audit (
  audit_id bigserial PRIMARY KEY,
  conversation_id uuid NOT NULL,
  stage text NOT NULL,
  payload_json jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ce_audit_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES ce_conversation(conversation_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ce_audit_conversation ON ce_audit (conversation_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ce_conversation_history (
  history_id bigserial PRIMARY KEY,
  conversation_id uuid NOT NULL,
  entry_type text NOT NULL,
  role text NOT NULL,
  stage text NOT NULL,
  content_text text,
  payload_json jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ce_conversation_history_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES ce_conversation(conversation_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ce_conversation_history_conv ON ce_conversation_history (conversation_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ce_validation_snapshot (
  snapshot_id bigserial PRIMARY KEY,
  conversation_id uuid NOT NULL,
  intent_code varchar(64),
  state_code varchar(64),
  validation_tables jsonb,
  validation_decision text,
  created_at timestamptz NOT NULL DEFAULT now(),
  schema_id bigint
);
CREATE INDEX IF NOT EXISTS idx_ce_validation_snapshot_conv ON ce_validation_snapshot (conversation_id);

CREATE TABLE IF NOT EXISTS ce_llm_call_log (
  llm_call_id bigserial PRIMARY KEY,
  conversation_id uuid NOT NULL,
  intent_code text,
  state_code text,
  provider text NOT NULL,
  model text NOT NULL,
  temperature numeric(3, 2),
  prompt_text text NOT NULL,
  user_context text NOT NULL,
  response_text text,
  success boolean NOT NULL,
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_llm_log_conversation ON ce_llm_call_log (conversation_id);
CREATE INDEX IF NOT EXISTS idx_ce_llm_log_intent_state ON ce_llm_call_log (intent_code, state_code);

CREATE TABLE IF NOT EXISTS ce_mcp_tool (
  tool_id bigserial PRIMARY KEY,
  tool_code text NOT NULL UNIQUE,
  tool_group text NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  description text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_mcp_tool_enabled ON ce_mcp_tool (enabled, tool_group, tool_code);

CREATE TABLE IF NOT EXISTS ce_mcp_db_tool (
  tool_id bigint PRIMARY KEY,
  dialect text NOT NULL DEFAULT 'POSTGRES',
  sql_template text NOT NULL,
  param_schema jsonb NOT NULL,
  safe_mode boolean NOT NULL DEFAULT true,
  max_rows integer NOT NULL DEFAULT 200,
  created_at timestamptz NOT NULL DEFAULT now(),
  allowed_identifiers jsonb,
  CONSTRAINT ce_mcp_db_tool_tool_id_fkey FOREIGN KEY (tool_id) REFERENCES ce_mcp_tool(tool_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ce_mcp_db_tool_dialect ON ce_mcp_db_tool (dialect);

CREATE TABLE IF NOT EXISTS ce_container_config (
  id bigserial PRIMARY KEY,
  intent_code text NOT NULL,
  state_code text NOT NULL,
  page_id integer NOT NULL,
  section_id integer NOT NULL,
  container_id integer NOT NULL,
  input_param_name text NOT NULL,
  priority integer NOT NULL DEFAULT 1,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ce_validation_config_lookup ON ce_container_config (intent_code, state_code, enabled, priority);

-- ==================== MPS TABLES ====================

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
