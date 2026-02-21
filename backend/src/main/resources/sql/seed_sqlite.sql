-- Idempotent seed for Mapper Studio (SQLite)
INSERT INTO ce_config(config_id, config_type, config_key, config_value, enabled)
VALUES(1, 'AgentIntentResolver', 'MIN_CONFIDENCE', '0.55', 1),
(2, 'AgentIntentResolver', 'COLLISION_GAP_THRESHOLD', '0.2', 1),
(3, 'AgentIntentResolver', 'SYSTEM_PROMPT', 'You are an intent resolution agent for a conversational engine.

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

                 Rules:
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
                 - Keep state non-null when possible.', 1),
(4, 'AgentIntentResolver', 'USER_PROMPT', ' Context:
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

                Return JSON in the required schema only.', 1),
(5, 'AgentIntentCollisionResolver', 'SYSTEM_PROMPT', 'You are a workflow assistant handling ambiguous intent collisions.
Use followups first when present.
Ask one concise disambiguation question.
If followups is empty, ask user to choose from top intents.', 1),
(6, 'AgentIntentCollisionResolver', 'USER_PROMPT', '
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
', 1),
(7, 'AgentIntentCollisionResolver', 'DERIVATION_HINT', 'When multiple intents have similar scores, derive a new intent to disambiguate.
                Consider followup questions, top intent scores, and conversation history.', 1),
(8, 'McpPlanner', 'SYSTEM_PROMPT', '
You are an MCP planning agent inside ConvEngine.

You will receive:
- user_input
- contextJson (may contain prior tool observations)
- available tools (DB-driven list)

Your job:
1) Decide the next step:
   - CALL_TOOL (choose a tool_code and args)
   - ANSWER (when enough observations exist)
2) Be conservative and safe.
3) Prefer getting schema first if schema is missing AND the question needs DB knowledge.

Rules:
- Never invent tables/columns. If unknown, call postgres.schema first.
- For postgres.query, choose identifiers only if schema observation confirms them.
- Keep args minimal.
- If user question is ambiguous, return ANSWER with an answer that asks ONE clarifying question.

Return JSON ONLY.
', 1),
(9, 'McpPlanner', 'USER_PROMPT', '
User input:
{{user_input}}

Context JSON:
{{context}}

Available MCP tools:
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
', 1),
(10, 'IntentResolutionStep', 'STICKY_INTENT', 'true', 1),
(11, 'DialogueActStep', 'SYSTEM_PROMPT', 'You are a dialogue-act classifier.
Return JSON only with:
{"dialogueAct":"AFFIRM|NEGATE|EDIT|RESET|QUESTION|NEW_REQUEST","confidence":0.0}', 1),
(12, 'DialogueActStep', 'USER_PROMPT', 'User text:
%s', 1),
(13, 'DialogueActStep', 'SCHEMA_PROMPT', '{
  "type":"object",
  "required":["dialogueAct","confidence"],
  "properties":{
    "dialogueAct":{"type":"string","enum":["AFFIRM","NEGATE","EDIT","RESET","QUESTION","NEW_REQUEST"]},
    "confidence":{"type":"number"}
  },
  "additionalProperties":false
}', 1)
ON CONFLICT(config_type, config_key) DO UPDATE SET
    config_value = excluded.config_value,
    enabled = excluded.enabled,
    created_at = excluded.created_at;


INSERT INTO ce_mcp_tool (tool_id, tool_code, tool_group, intent_code, state_code, enabled, description)
VALUES
  (1, 'postgres.schema', 'DB', NULL, NULL, 1, 'List table/column metadata'),
  (2, 'postgres.query', 'DB', NULL, NULL, 1, 'Run safe parameterized SQL query')
ON CONFLICT(tool_code) DO UPDATE SET
  tool_group = excluded.tool_group,
  intent_code = excluded.intent_code,
  state_code = excluded.state_code,
  enabled = excluded.enabled,
  description = excluded.description;

DELETE FROM ce_rule WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_response WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_prompt_template WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_output_schema WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_intent_classifier WHERE intent_code = 'MAPPING_STUDIO';
DELETE FROM ce_intent WHERE intent_code = 'MAPPING_STUDIO';

INSERT INTO ce_intent (intent_code, description, priority, enabled, display_name, llm_hint)
VALUES ('MAPPING_STUDIO', 'Mapping studio flow for source-to-target schema mapping', 20, 1, 'Mapping Studio', 'Use for schema mapping and publish workflow');

INSERT INTO ce_intent_classifier (intent_code, rule_type, pattern, priority, enabled, description)
VALUES
('MAPPING_STUDIO', 'CONTAINS', 'mapping studio', 10, 1, 'Mapping Studio phrase'),
('MAPPING_STUDIO', 'REGEX', '(?i).*(map|mapping).*(schema|xml|json|openapi|soap|db).*', 20, 1, 'Schema mapping intent'),
('MAPPING_STUDIO', 'REGEX', '(?i).*(publish|approve|confirm|generate|download|export).*(excel|xlsx|mapping|artifact).*', 25, 1, 'Publish/export mapping intent');

INSERT INTO ce_output_schema (intent_code, state_code, json_schema, description, enabled, priority)
VALUES (
'MAPPING_STUDIO',
'COLLECT_INPUTS',
'{"type":"object","properties":{"projectCode":{"type":"string"},"mappingVersion":{"type":"string"},"sourceType":{"type":"string","enum":["XML","JSON","DATABASE"]},"sourceSpec":{"type":"string"},"targetType":{"type":"string","enum":["JSON","JSON_SCHEMA","XML","XSD","XSD+WSDL"]},"targetSchema":{"type":"string"}},"required":["projectCode","mappingVersion","sourceType","sourceSpec","targetSchema"]}',
'Collect required mapping setup inputs',
1,
1
);

INSERT INTO ce_prompt_template (intent_code, state_code, response_type, system_prompt, user_prompt, temperature, enabled)
VALUES
('MAPPING_STUDIO', 'COLLECT_INPUTS', 'SCHEMA_JSON',
 'Extract mapping setup fields from user input. Return only valid JSON matching schema.',
 'User: {{user_input}} Context: {{context}} Missing: {{missing_fields}}',
 0.0,
 1),
('MAPPING_STUDIO', 'SUGGEST_READY', 'TEXT',
 'Summarize suggested mappings clearly with confidence and reason.',
 'Suggestions: {{mapping_suggestions}}',
 0.2,
 1),
('MAPPING_STUDIO', 'VALIDATE_READY', 'TEXT',
 'Summarize validation with blockers and final recommendation.',
 'Validation: {{validation_report}}',
 0.1,
 1),
('MAPPING_STUDIO', 'PUBLISHED', 'TEXT',
 'Return publish confirmation details.',
 'Publish: {{publish_result}}',
 0.0,
 1);

INSERT INTO ce_response (intent_code, state_code, output_format, response_type, exact_text, derivation_hint, priority, enabled, description)
VALUES
('MAPPING_STUDIO', 'COLLECT_INPUTS', 'TEXT', 'EXACT', 'Provide projectCode, mappingVersion, sourceType (XML/JSON/DATABASE), sourceSpec, targetType (JSON/JSON_SCHEMA/XML/XSD/XSD+WSDL), targetSchema.', null, 10, 1, 'Collect inputs'),
('MAPPING_STUDIO', 'PARSE_READY', 'TEXT', 'EXACT', 'Parsing schema artifacts now.', null, 20, 1, 'Parse start'),
('MAPPING_STUDIO', 'SUGGEST_READY', 'TEXT', 'DERIVED', null, 'Summarize mapping suggestions.', 30, 1, 'Suggestion summary'),
('MAPPING_STUDIO', 'VALIDATE_READY', 'TEXT', 'DERIVED', null, 'Summarize mapping validation report.', 40, 1, 'Validation summary'),
('MAPPING_STUDIO', 'AWAITING_CONFIRMATION', 'TEXT', 'EXACT', 'Reply publish/confirm/generate excel/download excel to finalize, or edit to revise.', null, 50, 1, 'Await confirmation'),
('MAPPING_STUDIO', 'PUBLISHED', 'TEXT', 'DERIVED', null, 'Return published artifact details.', 60, 1, 'Published summary');

INSERT INTO ce_rule (intent_code, state_code, rule_type, match_pattern, action, action_value, priority, enabled, description, phase)
VALUES
('MAPPING_STUDIO', 'ANY', 'JSON_PATH', '$[?(@.intent == ''MAPPING_STUDIO'' && (@.state == ''UNKNOWN'' || @.state == ''IDLE''))]', 'SET_STATE', 'COLLECT_INPUTS', 5, 1, 'Bootstrap Mapping Studio state after agent intent resolve', 'AGENT_POST_INTENT'),
('MAPPING_STUDIO', 'ANY', 'REGEX', '(?i)^(reset|restart|start over)$', 'SET_STATE', 'COLLECT_INPUTS', 10, 1, 'Reset flow', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'ANY', 'REGEX', '(?i)^(edit|revise|change)$', 'SET_STATE', 'COLLECT_INPUTS', 20, 1, 'Edit flow', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'COLLECT_INPUTS', 'JSON_PATH', '$[?(@.state == ''COLLECT_INPUTS'' && @.projectCode && @.projectCode != '''' && @.mappingVersion && @.mappingVersion != '''' && @.sourceType && @.sourceType != '''' && @.sourceSpec && @.sourceSpec != '''' && @.targetSchema && @.targetSchema != '''')]', 'SET_STATE', 'PARSE_READY', 100, 1, 'All required fields available', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'PARSE_READY', 'JSON_PATH', '$[?(@.state == ''PARSE_READY'')]', 'SET_TASK', 'mappingStudioTask:parseSchemas', 200, 1, 'Parse schemas', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'PARSE_READY', 'JSON_PATH', '$[?(@.state == ''PARSE_READY'' && @.inputParams.parse_status == ''DONE'')]', 'SET_STATE', 'SUGGEST_READY', 210, 1, 'Move to suggest', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'SUGGEST_READY', 'JSON_PATH', '$[?(@.state == ''SUGGEST_READY'')]', 'SET_TASK', 'mappingStudioTask:generateSuggestions', 300, 1, 'Generate mapping suggestions', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'SUGGEST_READY', 'JSON_PATH', '$[?(@.state == ''SUGGEST_READY'' && @.inputParams.suggestion_status == ''DONE'')]', 'SET_STATE', 'VALIDATE_READY', 310, 1, 'Move to validate', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'VALIDATE_READY', 'JSON_PATH', '$[?(@.state == ''VALIDATE_READY'')]', 'SET_TASK', 'mappingStudioTask:validateMappings', 400, 1, 'Validate mappings', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'VALIDATE_READY', 'JSON_PATH', '$[?(@.state == ''VALIDATE_READY'' && @.inputParams.validation_status == ''DONE'')]', 'SET_STATE', 'AWAITING_CONFIRMATION', 410, 1, 'Await user confirmation', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'ANY', 'REGEX', '(?i)^\s*(publish|confirm|approve|yes|generate\s+excel|download\s+excel|export\s+excel|generate\s+xlsx|download\s+xlsx|export\s+xlsx)\s*$', 'SET_TASK', 'mappingStudioTask:publishMappingArtifact', 500, 1, 'Publish artifacts', 'PIPELINE_RULES'),
('MAPPING_STUDIO', 'AWAITING_CONFIRMATION', 'JSON_PATH', '$[?(@.state == ''AWAITING_CONFIRMATION'' && @.inputParams.publish_status == ''DONE'')]', 'SET_STATE', 'PUBLISHED', 510, 1, 'Final published state', 'PIPELINE_RULES');
