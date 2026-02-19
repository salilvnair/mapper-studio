package com.salilvnair.mapperstudio.task.service;

import com.github.salilvnair.convengine.engine.session.EngineSession;
import com.salilvnair.mapperstudio.task.model.StudioSessionKeys;
import com.salilvnair.mapperstudio.task.model.TargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MappingStudioPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final SessionInputService sessionInputService;
    private final SchemaParserService schemaParserService;

    public void persistVersionIfMissing(EngineSession session) {
        String projectCode = sessionInputService.readSessionValue(session, StudioSessionKeys.PROJECT_CODE, StudioSessionKeys.DEFAULT_PROJECT_CODE);
        String version = sessionInputService.readSessionValue(session, StudioSessionKeys.MAPPING_VERSION, StudioSessionKeys.DEFAULT_MAPPING_VERSION);
        String sourceType = sessionInputService.readSessionValue(session, StudioSessionKeys.SOURCE_TYPE, StudioSessionKeys.DEFAULT_SOURCE_TYPE);
        String targetSchema = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA, StudioSessionKeys.DEFAULT_TARGET_SCHEMA);
        String targetSchemaJson = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_JSON, "");
        String targetSchemaXsd = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_XSD, "");
        String targetSchemaWsdl = sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_SCHEMA_WSDL, "");
        TargetType targetType = TargetType.resolve(sessionInputService.readSessionValue(session, StudioSessionKeys.TARGET_TYPE, StudioSessionKeys.DEFAULT_TARGET_TYPE), targetSchema);
        String effectiveTargetSchema = schemaParserService.resolveEffectiveTargetSchema(targetType, targetSchema, targetSchemaJson, targetSchemaXsd, targetSchemaWsdl);
        String targetSchemaPayload = schemaParserService.toTargetSchemaPayload(targetType, effectiveTargetSchema, targetSchemaXsd, targetSchemaWsdl);

        jdbcTemplate.update(
                "insert into mps_mapping_project(project_code, project_name, source_type, created_by) values (?, ?, ?, ?) on conflict (project_code) do nothing",
                projectCode, projectCode, sourceType, "studio-user"
        );

        jdbcTemplate.update(
                "insert into mps_mapping_version(project_code, version_code, status, target_schema_json, created_by) values (?, ?, 'DRAFT', ?, ?) on conflict (project_code, version_code) do nothing",
                projectCode, version, targetSchemaPayload, "studio-user"
        );
    }

    public void publish(EngineSession session) {
        if (!StudioSessionKeys.STATE_AWAITING_CONFIRMATION.equalsIgnoreCase(String.valueOf(session.getState()))) {
            session.putInputParam(StudioSessionKeys.PUBLISH_STATUS, StudioSessionKeys.STATUS_SKIPPED);
            session.putInputParam(StudioSessionKeys.PUBLISH_RESULT, Map.of(
                    "skipped", true,
                    "reason", "Publish allowed only from AWAITING_CONFIRMATION"
            ));
            return;
        }

        String projectCode = sessionInputService.readSessionValue(session, StudioSessionKeys.PROJECT_CODE, StudioSessionKeys.DEFAULT_PROJECT_CODE);
        String version = sessionInputService.readSessionValue(session, StudioSessionKeys.MAPPING_VERSION, StudioSessionKeys.DEFAULT_MAPPING_VERSION);
        String artifactId = UUID.randomUUID().toString();

        jdbcTemplate.update(
                "update mps_mapping_version set status='PUBLISHED', published_at=?, artifact_id=? where project_code=? and version_code=?",
                OffsetDateTime.now(), artifactId, projectCode, version
        );

        session.putInputParam(StudioSessionKeys.PUBLISH_STATUS, StudioSessionKeys.STATUS_DONE);
        session.putInputParam(StudioSessionKeys.PUBLISH_RESULT, Map.of(
                "projectCode", projectCode,
                "version", version,
                "artifactId", artifactId,
                "publishedAt", OffsetDateTime.now().toString()
        ));
    }
}
