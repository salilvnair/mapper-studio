package com.obe.mapperstudio.api.dto;

import java.util.List;

public record DbInitResponse(
        boolean success,
        List<String> schemaExecuted,
        List<String> dataExecuted,
        String executedAt
) {}
