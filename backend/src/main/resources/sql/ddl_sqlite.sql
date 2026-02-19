PRAGMA foreign_keys = ON;

-- ==================== CE TABLES ====================

CREATE TABLE IF NOT EXISTS ce_config (
  config_id INTEGER PRIMARY KEY,
  config_type TEXT NOT NULL,
  config_key TEXT NOT NULL,
  config_value TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ce_config_type_key ON ce_config (config_type, config_key);

CREATE TABLE IF NOT EXISTS ce_intent (
  intent_code TEXT PRIMARY KEY,
  description TEXT NOT NULL,
  priority INTEGER NOT NULL DEFAULT 100,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  display_name TEXT,
  llm_hint TEXT
);
CREATE INDEX IF NOT EXISTS ix_ce_intent_enabled_priority ON ce_intent (enabled, priority, intent_code);

CREATE TABLE IF NOT EXISTS ce_intent_classifier (
  classifier_id INTEGER PRIMARY KEY AUTOINCREMENT,
  intent_code TEXT NOT NULL,
  rule_type TEXT NOT NULL,
  pattern TEXT NOT NULL,
  priority INTEGER NOT NULL,
  enabled INTEGER DEFAULT 1,
  description TEXT,
  FOREIGN KEY(intent_code) REFERENCES ce_intent(intent_code)
);

CREATE TABLE IF NOT EXISTS ce_output_schema (
  schema_id INTEGER PRIMARY KEY AUTOINCREMENT,
  intent_code TEXT NOT NULL,
  state_code TEXT NOT NULL,
  json_schema TEXT NOT NULL,
  description TEXT,
  enabled INTEGER DEFAULT 1,
  priority INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ce_output_schema_lookup ON ce_output_schema (intent_code, state_code, enabled, priority);

CREATE TABLE IF NOT EXISTS ce_prompt_template (
  template_id INTEGER PRIMARY KEY AUTOINCREMENT,
  intent_code TEXT,
  state_code TEXT,
  response_type TEXT NOT NULL,
  system_prompt TEXT NOT NULL,
  user_prompt TEXT NOT NULL,
  temperature REAL NOT NULL DEFAULT 0.0,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_prompt_template_lookup ON ce_prompt_template (response_type, intent_code, state_code, enabled);

CREATE TABLE IF NOT EXISTS ce_response (
  response_id INTEGER PRIMARY KEY AUTOINCREMENT,
  intent_code TEXT,
  state_code TEXT NOT NULL,
  output_format TEXT NOT NULL,
  response_type TEXT NOT NULL,
  exact_text TEXT,
  derivation_hint TEXT,
  json_schema TEXT,
  priority INTEGER NOT NULL DEFAULT 100,
  enabled INTEGER NOT NULL DEFAULT 1,
  description TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_response_intent_state ON ce_response (intent_code, state_code, enabled, priority);
CREATE INDEX IF NOT EXISTS idx_ce_response_lookup ON ce_response (state_code, enabled, priority);

CREATE TABLE IF NOT EXISTS ce_rule (
  rule_id INTEGER PRIMARY KEY AUTOINCREMENT,
  phase TEXT NOT NULL DEFAULT 'PIPELINE_RULES',
  intent_code TEXT,
  state_code TEXT,
  rule_type TEXT NOT NULL,
  match_pattern TEXT NOT NULL,
  action TEXT NOT NULL,
  action_value TEXT,
  priority INTEGER NOT NULL DEFAULT 100,
  enabled INTEGER NOT NULL DEFAULT 1,
  description TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_rule_priority ON ce_rule (enabled, phase, state_code, priority);

CREATE TABLE IF NOT EXISTS ce_policy (
  policy_id INTEGER PRIMARY KEY AUTOINCREMENT,
  rule_type TEXT NOT NULL,
  pattern TEXT NOT NULL,
  response_text TEXT NOT NULL,
  priority INTEGER NOT NULL DEFAULT 10,
  enabled INTEGER NOT NULL DEFAULT 1,
  description TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_policy_priority ON ce_policy (enabled, priority);

CREATE TABLE IF NOT EXISTS ce_conversation (
  conversation_id TEXT PRIMARY KEY,
  status TEXT NOT NULL,
  intent_code TEXT,
  state_code TEXT NOT NULL,
  context_json TEXT NOT NULL DEFAULT '{}',
  last_user_text TEXT,
  last_assistant_json TEXT,
  input_params_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_conversation_status ON ce_conversation (status);
CREATE INDEX IF NOT EXISTS idx_ce_conversation_updated ON ce_conversation (updated_at);

CREATE TABLE IF NOT EXISTS ce_audit (
  audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id TEXT NOT NULL,
  stage TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(conversation_id) REFERENCES ce_conversation(conversation_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ce_audit_conversation ON ce_audit (conversation_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ce_conversation_history (
  history_id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id TEXT NOT NULL,
  entry_type TEXT NOT NULL,
  role TEXT NOT NULL,
  stage TEXT NOT NULL,
  content_text TEXT,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(conversation_id) REFERENCES ce_conversation(conversation_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ce_conversation_history_conv ON ce_conversation_history (conversation_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ce_validation_snapshot (
  snapshot_id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id TEXT NOT NULL,
  intent_code TEXT,
  state_code TEXT,
  validation_tables TEXT,
  validation_decision TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  schema_id INTEGER
);
CREATE INDEX IF NOT EXISTS idx_ce_validation_snapshot_conv ON ce_validation_snapshot (conversation_id);

CREATE TABLE IF NOT EXISTS ce_llm_call_log (
  llm_call_id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id TEXT NOT NULL,
  intent_code TEXT,
  state_code TEXT,
  provider TEXT NOT NULL,
  model TEXT NOT NULL,
  temperature REAL,
  prompt_text TEXT NOT NULL,
  user_context TEXT NOT NULL,
  response_text TEXT,
  success INTEGER NOT NULL,
  error_message TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_llm_log_conversation ON ce_llm_call_log (conversation_id);
CREATE INDEX IF NOT EXISTS idx_ce_llm_log_intent_state ON ce_llm_call_log (intent_code, state_code);

CREATE TABLE IF NOT EXISTS ce_mcp_tool (
  tool_id INTEGER PRIMARY KEY AUTOINCREMENT,
  tool_code TEXT NOT NULL UNIQUE,
  tool_group TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  description TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_mcp_tool_enabled ON ce_mcp_tool (enabled, tool_group, tool_code);

CREATE TABLE IF NOT EXISTS ce_mcp_db_tool (
  tool_id INTEGER PRIMARY KEY,
  dialect TEXT NOT NULL DEFAULT 'POSTGRES',
  sql_template TEXT NOT NULL,
  param_schema TEXT NOT NULL,
  safe_mode INTEGER NOT NULL DEFAULT 1,
  max_rows INTEGER NOT NULL DEFAULT 200,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  allowed_identifiers TEXT,
  FOREIGN KEY(tool_id) REFERENCES ce_mcp_tool(tool_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ce_mcp_db_tool_dialect ON ce_mcp_db_tool (dialect);

CREATE TABLE IF NOT EXISTS ce_container_config (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  intent_code TEXT NOT NULL,
  state_code TEXT NOT NULL,
  page_id INTEGER NOT NULL,
  section_id INTEGER NOT NULL,
  container_id INTEGER NOT NULL,
  input_param_name TEXT NOT NULL,
  priority INTEGER NOT NULL DEFAULT 1,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ce_validation_config_lookup ON ce_container_config (intent_code, state_code, enabled, priority);

-- ==================== MPS TABLES ====================

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
