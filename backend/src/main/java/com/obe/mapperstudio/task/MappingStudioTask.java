package com.obe.mapperstudio.task;

import com.github.salilvnair.convengine.engine.rule.task.CeRuleTask;
import com.github.salilvnair.convengine.engine.session.EngineSession;
import com.github.salilvnair.convengine.entity.CeRule;
import com.obe.mapperstudio.task.model.StudioSessionKeys;
import com.obe.mapperstudio.task.model.TargetType;
import com.obe.mapperstudio.task.service.MappingStudioPersistenceService;
import com.obe.mapperstudio.task.service.MappingSuggestionService;
import com.obe.mapperstudio.task.service.MappingValidationService;
import com.obe.mapperstudio.task.service.SchemaParserService;
import com.obe.mapperstudio.task.service.SessionInputService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component("mappingStudioTask")
@RequiredArgsConstructor
public class MappingStudioTask implements CeRuleTask {

    private final SessionInputService sessionInputService;
    private final SchemaParserService schemaParserService;
    private final MappingSuggestionService mappingSuggestionService;
    private final MappingValidationService mappingValidationService;
    private final MappingStudioPersistenceService mappingStudioPersistenceService;

    public void parseSchemas(EngineSession session, CeRule rule) {
        sessionInputService.clearTransientStatuses(session);

        String sourceSpec = sessionInputService.readSessionValue(session, StudioSessionKeys.SOURCE_SPEC, StudioSessionKeys.DEFAULT_SOURCE_SPEC);
        String targetSchema = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA, StudioSessionKeys.DEFAULT_TARGET_SCHEMA);
        String targetSchemaJson = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_JSON, "");
        String targetSchemaXsd = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_XSD, "");
        String targetSchemaWsdl = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_WSDL, "");
        String targetSchemaXsdName = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_XSD_NAME, StudioSessionKeys.DEFAULT_TARGET_SCHEMA_XSD_NAME);
        String targetSchemaWsdlName = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_WSDL_NAME, StudioSessionKeys.DEFAULT_TARGET_SCHEMA_WSDL_NAME);
        List<Map<String, Object>> targetSchemaXsdList = sessionInputService.readArtifactList(session, StudioSessionKeys.TARGET_SCHEMA_XSD_LIST);

        TargetType targetType = TargetType.resolve(sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_TYPE, StudioSessionKeys.DEFAULT_TARGET_TYPE), targetSchema);
        String effectiveTargetSchema = schemaParserService.resolveEffectiveTargetSchema(
                targetType,
                targetSchema,
                targetSchemaJson,
                targetSchemaXsd,
                targetSchemaWsdl
        );

        List<Map<String, Object>> sourceFields = schemaParserService.parseSourceFields(sourceSpec);
        List<Map<String, Object>> targetFields = schemaParserService.parseTargetFields(
                effectiveTargetSchema,
                targetType,
                targetSchemaXsd,
                targetSchemaWsdl,
                targetSchemaXsdName,
                targetSchemaWsdlName,
                targetSchemaXsdList
        );

        session.putInputParam(StudioSessionKeys.PARSED_SOURCE_FIELDS, sourceFields);
        session.putInputParam(StudioSessionKeys.PARSED_TARGET_FIELDS, targetFields);
        session.putInputParam(StudioSessionKeys.TARGET_TYPE_NORMALIZED, targetType.name());
        session.putInputParam(StudioSessionKeys.PARSE_STATUS, StudioSessionKeys.STATUS_DONE);
        session.putInputParam(StudioSessionKeys.PARSE_RESULT, "Parsed source + target schema successfully (" + targetType.name() + ")");

        mappingStudioPersistenceService.persistVersionIfMissing(session);
    }

    public void generateSuggestions(EngineSession session, CeRule rule) {
        List<Map<String, Object>> sourceFields = sessionInputService.readFieldList(session.getInputParams().get(StudioSessionKeys.PARSED_SOURCE_FIELDS));
        List<Map<String, Object>> targetFields = sessionInputService.readFieldList(session.getInputParams().get(StudioSessionKeys.PARSED_TARGET_FIELDS));
        List<Map<String, Object>> suggestions = mappingSuggestionService.generateSuggestions(sourceFields, targetFields);
        session.putInputParam(StudioSessionKeys.MAPPING_SUGGESTIONS, suggestions);
        session.putInputParam(StudioSessionKeys.SUGGESTION_STATUS, StudioSessionKeys.STATUS_DONE);
    }

    public void validateMappings(EngineSession session, CeRule rule) {
        Map<String, Object> validation = mappingValidationService.defaultValidationReport();
        session.putInputParam(StudioSessionKeys.VALIDATION_REPORT, validation);
        session.putInputParam(StudioSessionKeys.MISSING_REQUIRED, Collections.emptyList());
        session.putInputParam(StudioSessionKeys.TYPE_MISMATCH, Collections.emptyList());
        session.putInputParam(StudioSessionKeys.DUPLICATE_TARGETS, Collections.emptyList());
        session.putInputParam(StudioSessionKeys.VALIDATION_STATUS, StudioSessionKeys.STATUS_DONE);
    }

    public void publishMappingArtifact(EngineSession session, CeRule rule) {
        mappingStudioPersistenceService.publish(session);
    }
}
