INSERT INTO ce_intent (intent_code, description, priority, enabled, display_name, llm_hint)
VALUES ('MAPPING_STUDIO', 'Mapping studio flow for source-to-target schema mapping', 20, 1, 'Mapping Studio', 'Use for schema mapping and publish workflow')
ON CONFLICT (intent_code) DO NOTHING;

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

INSERT INTO ce_rule (intent_code, rule_type, match_pattern, action, action_value, priority, enabled, description)
VALUES
('MAPPING_STUDIO', 'REGEX', '(?i)^(reset|restart|start over)$', 'SET_STATE', 'COLLECT_INPUTS', 10, 1, 'Reset flow'),
('MAPPING_STUDIO', 'REGEX', '(?i)^(edit|revise|change)$', 'SET_STATE', 'COLLECT_INPUTS', 20, 1, 'Edit flow'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''COLLECT_INPUTS'' && @.projectCode && @.mappingVersion && @.sourceType && @.sourceSpec && @.targetSchema)]', 'SET_STATE', 'PARSE_READY', 100, 1, 'All required fields available'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''PARSE_READY'')]', 'SET_TASK', 'mappingStudioTask:parseSchemas', 200, 1, 'Parse schemas'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''PARSE_READY'' && @.inputParams.parse_status == ''DONE'')]', 'SET_STATE', 'SUGGEST_READY', 210, 1, 'Move to suggest'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''SUGGEST_READY'')]', 'SET_TASK', 'mappingStudioTask:generateSuggestions', 300, 1, 'Generate mapping suggestions'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''SUGGEST_READY'' && @.inputParams.suggestion_status == ''DONE'')]', 'SET_STATE', 'VALIDATE_READY', 310, 1, 'Move to validate'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''VALIDATE_READY'')]', 'SET_TASK', 'mappingStudioTask:validateMappings', 400, 1, 'Validate mappings'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''VALIDATE_READY'' && @.inputParams.validation_status == ''DONE'')]', 'SET_STATE', 'AWAITING_CONFIRMATION', 410, 1, 'Await user confirmation'),
('MAPPING_STUDIO', 'REGEX', '(?i)^(publish|confirm|approve|yes|generate excel|download excel|export excel|generate xlsx|download xlsx|export xlsx)$', 'SET_TASK', 'mappingStudioTask:publishMappingArtifact', 500, 1, 'Publish artifacts'),
('MAPPING_STUDIO', 'JSON_PATH', '$[?(@.state == ''AWAITING_CONFIRMATION'' && @.inputParams.publish_status == ''DONE'')]', 'SET_STATE', 'PUBLISHED', 510, 1, 'Final published state');
