package com.obe.mapperstudio.service.studio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obe.mapperstudio.api.dto.MappingConfirmResponse;
import com.obe.mapperstudio.api.dto.MappingExportRequest;
import com.obe.mapperstudio.api.dto.MappingExportRow;
import com.obe.mapperstudio.api.dto.MappingSaveResponse;
import com.obe.mapperstudio.service.studio.enums.MappingOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MappingManagementService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper;

    public MappingSaveResponse saveMappings(MappingExportRequest request) {
        int savedCount = persistMappings(request);
        long selectedCount = mappingRows(request).stream().filter(m -> Boolean.TRUE.equals(m.selected())).count();
        return new MappingSaveResponse(
                request.projectCode(),
                request.mappingVersion(),
                savedCount,
                (int) selectedCount,
                OffsetDateTime.now().toString()
        );
    }

    public MappingConfirmResponse confirmMappings(MappingExportRequest request) {
        int selectedCount = (int) mappingRows(request).stream().filter(m -> Boolean.TRUE.equals(m.selected())).count();
        if (selectedCount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No selected mappings available for confirmation");
        }

        String projectCode = safeText(request.projectCode(), "MAPPER_DEMO_PROJECT");
        String versionCode = safeText(request.mappingVersion(), "1.0.0");

        jdbcTemplate.update(
                "insert into obe_mapping_manual_confirm_audit(project_code, version_code, confirmed, confirmed_by, selected_count, mapping_snapshot, notes) values (?, ?, true, ?, ?, ?, ?)",
                projectCode,
                versionCode,
                "studio-user",
                selectedCount,
                toMappingsSnapshotJson(request),
                "Manual confirmation from studio UI"
        );

        return new MappingConfirmResponse(projectCode, versionCode, true, selectedCount, OffsetDateTime.now().toString());
    }

    public void validateExportAllowed(String projectCode, String versionCode) {
        if (!hasManualConfirmation(projectCode, versionCode)) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Manual confirmation required before export");
        }
    }

    private int persistMappings(MappingExportRequest request) {
        String projectCode = safeText(request.projectCode(), "MAPPER_DEMO_PROJECT");
        String versionCode = safeText(request.mappingVersion(), "1.0.0");
        String sourceType = request.sourceType() == null ? "OPENAPI" : request.sourceType();

        jdbcTemplate.update(
                "insert into obe_mapping_project(project_code, project_name, source_type, created_by) values (?, ?, ?, ?) on conflict (project_code) do nothing",
                projectCode, projectCode, sourceType, "studio-user"
        );

        jdbcTemplate.update(
                "insert into obe_mapping_version(project_code, version_code, status, target_schema_json, created_by) values (?, ?, 'DRAFT', ?, ?) on conflict (project_code, version_code) do nothing",
                projectCode, versionCode, "{}", "studio-user"
        );

        jdbcTemplate.update("delete from obe_mapping_field where project_code=? and version_code=?", projectCode, versionCode);
        clearManualConfirmation(projectCode, versionCode);

        int saved = 0;
        for (MappingExportRow row : mappingRows(request)) {
            if (!Boolean.TRUE.equals(row.selected())) {
                continue;
            }
            if (isBlank(row.sourcePath()) || isBlank(row.targetPath())) {
                continue;
            }
            jdbcTemplate.update(
                    "insert into obe_mapping_field(project_code, version_code, source_path, target_path, transform_type, transform_config, confidence, reasoning) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    projectCode,
                    versionCode,
                    row.sourcePath(),
                    row.targetPath(),
                    safeText(row.transformType(), "DIRECT"),
                    toTransformConfigJson(row),
                    BigDecimal.valueOf(row.confidence() == null ? 0d : row.confidence()),
                    safeText(row.notes(), safeText(row.reason(), ""))
            );
            saved++;
        }
        return saved;
    }

    private void clearManualConfirmation(String projectCode, String versionCode) {
        jdbcTemplate.update(
                "delete from obe_mapping_manual_confirm_audit where project_code=? and version_code=?",
                projectCode,
                versionCode
        );
    }

    private boolean hasManualConfirmation(String projectCode, String versionCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from obe_mapping_manual_confirm_audit where project_code=? and version_code=? and confirmed=true",
                Integer.class,
                projectCode,
                versionCode
        );
        return count != null && count > 0;
    }

    private String toMappingsSnapshotJson(MappingExportRequest request) {
        try {
            return mapper.writeValueAsString(mappingRows(request));
        } catch (JsonProcessingException ignored) {
            return "[]";
        }
    }

    private String toTransformConfigJson(MappingExportRow row) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("manualOverride", Boolean.TRUE.equals(row.manualOverride()));
        config.put("selected", Boolean.TRUE.equals(row.selected()));
        config.put("mappingOrigin", MappingOrigin.resolve(row.mappingOrigin(), Boolean.TRUE.equals(row.manualOverride())));
        if (!isBlank(row.targetArtifactName())) {
            config.put("targetArtifactName", row.targetArtifactName());
        }
        if (!isBlank(row.targetArtifactType())) {
            config.put("targetArtifactType", row.targetArtifactType());
        }
        try {
            return mapper.writeValueAsString(config);
        } catch (JsonProcessingException ignored) {
            return "{}";
        }
    }

    private List<MappingExportRow> mappingRows(MappingExportRequest request) {
        return request.mappings() == null ? List.of() : request.mappings();
    }

    private String safeText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
