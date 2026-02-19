package com.obe.mapperstudio.api.dto;

public record MappingConfirmResponse(
        String projectCode,
        String mappingVersion,
        boolean confirmed,
        int selectedCount,
        String confirmedAt
) {}
