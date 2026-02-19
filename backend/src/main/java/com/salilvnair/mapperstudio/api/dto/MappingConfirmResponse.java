package com.salilvnair.mapperstudio.api.dto;

public record MappingConfirmResponse(
        String projectCode,
        String mappingVersion,
        boolean confirmed,
        int selectedCount,
        String confirmedAt
) {}
