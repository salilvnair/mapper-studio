package com.obe.mapperstudio.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StudioMessageRequest(
        String conversationId,
        @NotBlank String message,
        Map<String, Object> inputParams
) {}
