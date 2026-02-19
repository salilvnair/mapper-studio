package com.salilvnair.mapperstudio.api.dto;

public record DbInitStatusResponse(
        boolean initialized,
        String status,
        String checkedAt
) {}
