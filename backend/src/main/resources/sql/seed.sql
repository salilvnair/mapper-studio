-- Idempotent seed for Mapper Studio (Postgres)

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (1, 'AgentIntentResolver', 'MIN_CONFIDENCE', '0.55', true, '2026-02-10 10:15:54.227')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (4, 'AgentIntentResolver', 'USER_PROMPT', $$ Context:
                {{context}}

                Allowed intents:
                {{allowed_intents}}

                Potential intent collisions:
                {{intent_collision_candidates}}

                Current intent scores:
                {{intent_scores}}

                Previous clarification question (if any):
                {{pending_clarification}}

                User input:
                {{user_input}}

                Return JSON in the required schema only.$$ , true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (9, 'McpPlanner', 'DB_USER_PROMPT', $$
User input:
{{user_input}}

Context JSON:
{{context}}

Available MCP DB tools:
{{mcp_tools}}

Existing MCP observations (if any):
{{mcp_observations}}

Return JSON EXACTLY in this schema:
{
  "action": "CALL_TOOL" | "ANSWER",
  "tool_code": "<tool_code_or_null>",
  "args": { },
  "answer": "<text_or_null>"
}
$$, true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (3, 'AgentIntentResolver', 'SYSTEM_PROMPT', $$You are an intent resolution agent for a conversational engine.
        You are a JSON generator. You must output valid JSON only. Do not include any explanations, greetings, or markdown formatting. Only return the JSON object.
                 Return JSON ONLY with fields:
                 {
                   "intent": "<INTENT_CODE_OR_NULL>",
                   "state": "INTENT_COLLISION | IDLE",
                   "confidence": 0.0,
                   "needsClarification": false,
                   "clarificationResolved": false,
                   "clarificationQuestion": "",
                   "intentScores": [{"intent":"<INTENT_CODE>","confidence":0.0}],
                   "followups": []
                 }
                CHAIN-OF-THOUGHT POLICY:
- Do NOT reveal chain-of-thought.
- Do NOT explain how you reached the answer.
- Summaries, reasoning, or internal thoughts are forbidden.
                 Rules:
CRITICAL OUTPUT RULES:
- DO NOT include reasoning, thoughts, or analysis.
- DO NOT use <think> tags or similar.
- Return ONLY valid JSON.
- intent MUST be the intent CODE, not an id or priority.
- confidence MUST be between 0.0 and 1.0.
- clarificationQuestion MUST be null when needsClarification=false.
                 - Score all plausible intents and return them in intentScores sorted by confidence descending.
                 - If top intents are close and ambiguous, set state to INTENT_COLLISION and needsClarification=true.
                 - For INTENT_COLLISION, add one follow-up disambiguation question in followups.
                 - If top intent is clear, set intent to best intent and confidence to best confidence.
                 - If user input is question-like (what/where/when/why/how/which/who/help/details/required/needed),
                   keep informational intents (like FAQ-style intents) in intentScores unless clearly impossible.
                 - When a domain/task intent and informational intent are both plausible for a question, keep both with close scores;
                   prefer INTENT_COLLISION instead of collapsing too early.
                 - Use only allowed intents.
                 - Do not hallucinate missing identifiers or facts.
                 - Keep state non-null when possible.$$ , true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (5, 'AgentIntentCollisionResolver', 'SYSTEM_PROMPT', $$You are a workflow assistant handling ambiguous intent collisions.
Use followups first when present.
Ask one concise disambiguation question.
If followups is empty, ask user to choose from top intents.$$ , true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (6, 'AgentIntentCollisionResolver', 'USER_PROMPT', $$
User message:
{{user_input}}

Followups:
{{followups}}

Top intent scores:
{{intent_top3}}

Session:
{{session}}

Context:
{{context}}
$$, true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (7, 'AgentIntentCollisionResolver', 'DERIVATION_HINT', $$When multiple intents have similar scores, derive a new intent to disambiguate.
                Consider followup questions, top intent scores, and conversation history.$$ , true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (2, 'AgentIntentResolver', 'COLLISION_GAP_THRESHOLD', '0.2', true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (8, 'McpPlanner', 'DB_SYSTEM_PROMPT', $$
You are an MCP DB planning agent inside ConvEngine.

You will receive:
- user_input
- contextJson (may contain prior tool observations)
- available MCP DB tools (from ce_mcp_tool + ce_mcp_db_tool)

Your job:
1) Decide the next step:
   - CALL_TOOL (choose a DB tool_code and args)
   - ANSWER (when enough observations exist)
2) Be conservative and safe.
3) Prefer getting DB schema first if schema is missing AND the question needs DB knowledge.

Rules:
- Never invent tables/columns. If unknown, call postgres.schema first.
- For postgres.query, choose identifiers only if schema observation confirms them.
- Keep args minimal.
- If user question is ambiguous, return ANSWER with an answer that asks ONE clarifying question.
- Do not plan non-DB tools in this planner.

Return JSON ONLY.
$$, true, '2026-02-10 10:15:54.230')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (101, 'IntentResolutionStep', 'STICKY_INTENT', 'true', true, '2026-02-17 00:30:05.741')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (102, 'DialogueActStep', 'convengine.dialogue.act.resolute', 'REGEX_THEN_LLM', true, '2026-02-20 10:00:00.000')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_config (config_id, config_type, config_key, config_value, enabled, created_at)
VALUES (103, 'DialogueActStep', 'convengine.dialogue.act.llm.threshold', '0.90', true, '2026-02-20 10:00:00.000')
ON CONFLICT (config_type, config_key) DO UPDATE SET
  config_value = EXCLUDED.config_value,
  enabled = EXCLUDED.enabled,
  created_at = EXCLUDED.created_at;

INSERT INTO ce_mcp_tool (tool_id, tool_code, tool_group, intent_code, state_code, enabled, description, created_at)
VALUES
  (1, 'postgres.schema', 'DB', null, null, true, 'List table/column metadata', now()),
  (2, 'postgres.query', 'DB', null, null, true, 'Run safe parameterized SQL query', now())
ON CONFLICT (tool_code) DO UPDATE SET
  tool_group = EXCLUDED.tool_group,
  intent_code = EXCLUDED.intent_code,
  state_code = EXCLUDED.state_code,
  enabled = EXCLUDED.enabled,
  description = EXCLUDED.description;

DELETE FROM ce_rule WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_response WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_prompt_template WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_output_schema WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_intent_classifier WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_intent WHERE intent_code = 'MAPPING_STUDIO';

INSERT INTO ce_intent (intent_code, description, priority, enabled, display_name, llm_hint)
VALUES ('MAPPING_STUDIO', 'Mapping studio flow for source-to-target schema mapping', 20, true, 'Mapping Studio', 'Use for schema mapping and publish workflow');

INSERT INTO ce_intent_classifier (intent_code, rule_type, pattern, priority, enabled, description)
VALUES
('MAPPING_STUDIO', 'CONTAINS', 'mapping studio', 10, true, 'Mapping Studio phrase'),
('MAPPING_STUDIO', 'REGEX', '(?i).*(map|mapping).*(schema|xml|json|openapi|soap|db).*', 20, true, 'Schema mapping intent'),
('MAPPING_STUDIO', 'REGEX', '(?i).*(publish|approve|confirm|generate|download|export).*(excel|xlsx|mapping|artifact).*', 25, true, 'Publish/export mapping intent');

INSERT INTO ce_output_schema (intent_code, state_code, json_schema, description, enabled, priority)
VALUES (
'MAPPING_STUDIO',
'COLLECT_INPUTS',
'{"type":"object","properties":{"projectCode":{"type":"string"},"mappingVersion":{"type":"string"},"sourceType":{"type":"string","enum":["XML","JSON","DATABASE"]},"sourceSpec":{"type":"string"},"targetType":{"type":"string","enum":["JSON","JSON_SCHEMA","XML","XSD","XSD+WSDL"]},"targetSchema":{"type":"string"}},"required":["projectCode","mappingVersion","sourceType","sourceSpec","targetSchema"]}'::jsonb,
'Collect required mapping setup inputs',
true,
1
);

INSERT INTO ce_prompt_template (intent_code, state_code, response_type, system_prompt, user_prompt, temperature, enabled)
VALUES
('MAPPING_STUDIO', 'COLLECT_INPUTS', 'SCHEMA_JSON',
 'Extract mapping setup fields from user input. Return only valid JSON matching schema.',
 'User: {{user_input}} Context: {{context}} Missing: {{missing_fields}}',
 0.0,
 true),
('MAPPING_STUDIO', 'SUGGEST_READY', 'TEXT',
 'Summarize suggested mappings clearly with confidence and reason.',
 'Suggestions: {{mapping_suggestions}}',
 0.2,
 true),
('MAPPING_STUDIO', 'VALIDATE_READY', 'TEXT',
 'Summarize validation with blockers and final recommendation.',
 'Validation: {{validation_report}}',
 0.1,
 true),
('MAPPING_STUDIO', 'PUBLISHED', 'TEXT',
 'Return publish confirmation details.',
 'Publish: {{publish_result}}',
 0.0,
 true);

INSERT INTO ce_response (intent_code, state_code, output_format, response_type, exact_text, derivation_hint, priority, enabled, description)
VALUES
('MAPPING_STUDIO', 'COLLECT_INPUTS', 'TEXT', 'EXACT', 'Provide projectCode, mappingVersion, sourceType (XML/JSON/DATABASE), sourceSpec, targetType (JSON/JSON_SCHEMA/XML/XSD/XSD+WSDL), targetSchema.', null, 10, true, 'Collect inputs'),
('MAPPING_STUDIO', 'PARSE_READY', 'TEXT', 'EXACT', 'Parsing schema artifacts now.', null, 20, true, 'Parse start'),
('MAPPING_STUDIO', 'SUGGEST_READY', 'TEXT', 'DERIVED', null, 'Summarize mapping suggestions.', 30, true, 'Suggestion summary'),
('MAPPING_STUDIO', 'VALIDATE_READY', 'TEXT', 'DERIVED', null, 'Summarize mapping validation report.', 40, true, 'Validation summary'),
('MAPPING_STUDIO', 'AWAITING_CONFIRMATION', 'TEXT', 'EXACT', 'Reply publish/confirm/generate excel/download excel to finalize, or edit to revise.', null, 50, true, 'Await confirmation'),
('MAPPING_STUDIO', 'PUBLISHED', 'TEXT', 'DERIVED', null, 'Return published artifact details.', 60, true, 'Published summary');

INSERT INTO ce_rule (intent_code, state_code, rule_type, match_pattern, action, action_value, priority, enabled, description, phase)
VALUES
('MAPPING_STUDIO', 'ANY', 'JSON_PATH', '$[?(@.intent == ''MAPPING_STUDIO'' && (@.state == ''UNKNOWN'' || @.state == ''IDLE''))]', 'SET_STATE', 'COLLECT_INPUTS', 5, true, 'Bootstrap Mapping Studio state after agent intent resolve', 'AGENT_POST_INTENT'),
('MAPPING_STUDIO', 'ANY', 'REGEX', '(?i)^(reset|restart|start over)$', 'SET_STATE', 'COLLECT_INPUTS', 10, true, 'Reset flow', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'ANY', 'REGEX', '(?i)^(edit|revise|change)$', 'SET_STATE', 'COLLECT_INPUTS', 20, true, 'Edit flow', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'COLLECT_INPUTS', 'JSON_PATH', '$[?(@.state == ''COLLECT_INPUTS'' && @.projectCode && @.projectCode != '''' && @.mappingVersion && @.mappingVersion != '''' && @.sourceType && @.sourceType != '''' && @.sourceSpec && @.sourceSpec != '''' && @.targetSchema && @.targetSchema != '''')]', 'SET_STATE', 'PARSE_READY', 100, true, 'All required fields available', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'PARSE_READY', 'JSON_PATH', '$[?(@.state == ''PARSE_READY'')]', 'SET_TASK', 'mappingStudioTask:parseSchemas', 200, true, 'Parse schemas', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'PARSE_READY', 'JSON_PATH', '$[?(@.state == ''PARSE_READY'' && @.inputParams.parse_status == ''DONE'')]', 'SET_STATE', 'SUGGEST_READY', 210, true, 'Move to suggest', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'SUGGEST_READY', 'JSON_PATH', '$[?(@.state == ''SUGGEST_READY'')]', 'SET_TASK', 'mappingStudioTask:generateSuggestions', 300, true, 'Generate mapping suggestions', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'SUGGEST_READY', 'JSON_PATH', '$[?(@.state == ''SUGGEST_READY'' && @.inputParams.suggestion_status == ''DONE'')]', 'SET_STATE', 'VALIDATE_READY', 310, true, 'Move to validate', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'VALIDATE_READY', 'JSON_PATH', '$[?(@.state == ''VALIDATE_READY'')]', 'SET_TASK', 'mappingStudioTask:validateMappings', 400, true, 'Validate mappings', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'VALIDATE_READY', 'JSON_PATH', '$[?(@.state == ''VALIDATE_READY'' && @.inputParams.validation_status == ''DONE'')]', 'SET_STATE', 'AWAITING_CONFIRMATION', 410, true, 'Await user confirmation', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'ANY', 'REGEX', '(?i)^\s*(publish|confirm|approve|yes|generate\s+excel|download\s+excel|export\s+excel|generate\s+xlsx|download\s+xlsx|export\s+xlsx)\s*$', 'SET_TASK', 'mappingStudioTask:publishMappingArtifact', 500, true, 'Publish artifacts', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'AWAITING_CONFIRMATION', 'JSON_PATH', '$[?(@.state == ''AWAITING_CONFIRMATION'' && @.inputParams.publish_status == ''DONE'')]', 'SET_STATE', 'PUBLISHED', 510, true, 'Final published state', 'PIPELINE_RULES');
