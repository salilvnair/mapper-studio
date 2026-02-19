package com.obe.mapperstudio.api.dto;

public record DbInitStatusResponse(
        boolean initialized,
        String status,
        String checkedAt
) {}
