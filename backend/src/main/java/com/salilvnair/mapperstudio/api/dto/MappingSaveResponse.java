package com.salilvnair.mapperstudio.api.dto;

public record MappingSaveResponse(
        String projectCode,
        String mappingVersion,
        int savedCount,
        int selectedCount,
        String savedAt
) {}
