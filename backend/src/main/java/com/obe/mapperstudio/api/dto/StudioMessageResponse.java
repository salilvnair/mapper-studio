package com.obe.mapperstudio.api.dto;

public record StudioMessageResponse(
        String conversationId,
        String intent,
        String state,
        String payloadType,
        Object payload,
        String contextJson
) {}
