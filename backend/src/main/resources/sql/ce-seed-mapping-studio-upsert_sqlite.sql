-- Idempotent update/upsert patch for publish/export phrase handling in Mapping Studio (SQLite)

INSERT INTO ce_intent (intent_code, description, priority, enabled, display_name, llm_hint)
VALUES ('MAPPING_STUDIO', 'Mapping studio flow for source-to-target schema mapping', 20, 1, 'Mapping Studio', 'Use for schema mapping and publish workflow')
ON CONFLICT (intent_code) DO NOTHING;

INSERT INTO ce_intent_classifier (intent_code, rule_type, pattern, priority, enabled, description)
SELECT 'MAPPING_STUDIO', 'REGEX', '(?i).*(publish|approve|confirm|generate|download|export).*(excel|xlsx|mapping|artifact).*', 25, 1, 'Publish/export mapping intent'
WHERE NOT EXISTS (
  SELECT 1
  FROM ce_intent_classifier
  WHERE intent_code = 'MAPPING_STUDIO'
    AND rule_type = 'REGEX'
    AND pattern = '(?i).*(publish|approve|confirm|generate|download|export).*(excel|xlsx|mapping|artifact).*'
);

UPDATE ce_response
SET exact_text = 'Reply publish/confirm/generate excel/download excel to finalize, or edit to revise.'
WHERE intent_code = 'MAPPING_STUDIO'
  AND state_code = 'AWAITING_CONFIRMATION'
  AND response_type = 'EXACT';

UPDATE ce_rule
SET match_pattern = '(?i)^(publish|confirm|approve|yes|generate excel|download excel|export excel|generate xlsx|download xlsx|export xlsx)$'
WHERE intent_code = 'MAPPING_STUDIO'
  AND action = 'SET_TASK'
  AND action_value = 'mappingStudioTask:publishMappingArtifact';

INSERT INTO ce_rule (intent_code, rule_type, match_pattern, action, action_value, priority, enabled, description)
SELECT
  'MAPPING_STUDIO',
  'REGEX',
  '(?i)^(publish|confirm|approve|yes|generate excel|download excel|export excel|generate xlsx|download xlsx|export xlsx)$',
  'SET_TASK',
  'mappingStudioTask:publishMappingArtifact',
  500,
  1,
  'Publish artifacts'
WHERE NOT EXISTS (
  SELECT 1
  FROM ce_rule
  WHERE intent_code = 'MAPPING_STUDIO'
    AND action = 'SET_TASK'
    AND action_value = 'mappingStudioTask:publishMappingArtifact'
);

INSERT INTO ce_rule (intent_code, rule_type, match_pattern, action, action_value, priority, enabled, description, phase)
SELECT
  'MAPPING_STUDIO',
  'JSON_PATH',
  '$[?(@.intent == ''MAPPING_STUDIO'' && (@.state == ''UNKNOWN'' || @.state == ''IDLE''))]',
  'SET_STATE',
  'COLLECT_INPUTS',
  5,
  1,
  'Bootstrap Mapping Studio state after agent intent resolve',
  'AGENT_POST_INTENT'
WHERE NOT EXISTS (
  SELECT 1
  FROM ce_rule
  WHERE intent_code = 'MAPPING_STUDIO'
    AND rule_type = 'JSON_PATH'
    AND action = 'SET_STATE'
    AND action_value = 'COLLECT_INPUTS'
    AND phase = 'AGENT_POST_INTENT'
);
