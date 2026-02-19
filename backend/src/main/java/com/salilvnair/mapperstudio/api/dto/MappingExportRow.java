package com.salilvnair.mapperstudio.api.dto;

public record MappingExportRow(
        String sourcePath,
        String targetPath,
        String transformType,
        Double confidence,
        String reason,
        String notes,
        String mappingOrigin,
        Boolean selected,
        Boolean manualOverride,
        String targetArtifactName,
        String targetArtifactType
) {}
