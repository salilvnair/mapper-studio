package com.salilvnair.mapperstudio.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record MappingExportRequest(
        @NotBlank String projectCode,
        @NotBlank String mappingVersion,
        @NotBlank String sourceType,
        @NotBlank String targetType,
        @NotBlank String pathType,
        List<MappingExportRow> mappings
) {}
