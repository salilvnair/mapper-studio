package com.salilvnair.mapperstudio.service.studio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salilvnair.mapperstudio.api.dto.StudioMessageRequest;
import com.salilvnair.mapperstudio.api.dto.StudioMessageResponse;
import com.github.salilvnair.convengine.engine.context.EngineContext;
import com.github.salilvnair.convengine.engine.core.ConversationalEngine;
import com.github.salilvnair.convengine.engine.model.EngineResult;
import com.github.salilvnair.convengine.model.JsonPayload;
import com.github.salilvnair.convengine.model.OutputPayload;
import com.github.salilvnair.convengine.model.TextPayload;
import com.salilvnair.mapperstudio.task.model.StudioSessionKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudioConversationService {

    private final ConversationalEngine engine;
    private final ObjectMapper objectMapper;

    public StudioMessageResponse process(StudioMessageRequest request) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();

        Map<String, Object> inputParams = new LinkedHashMap<>();
        if (request.inputParams() != null) {
            inputParams.putAll(request.inputParams());
        }
        inputParams.put(StudioSessionKeys.STUDIO_MODE, StudioSessionKeys.MODE_MAPPING_STUDIO);

        EngineContext context = EngineContext.builder()
                .conversationId(conversationId)
                .userText(request.message())
                .inputParams(inputParams)
                .build();

        try {
            EngineResult result = engine.process(context);
            return toResponse(conversationId, result);
        } catch (Exception ex) {
            return errorResponse(conversationId, ex);
        }
    }

    private StudioMessageResponse toResponse(String conversationId, EngineResult result) {
        String payloadType = "TEXT";
        Object payloadValue = "";
        OutputPayload payload = result.payload();
        if (payload instanceof TextPayload text) {
            payloadType = "TEXT";
            payloadValue = text.text();
        } else if (payload instanceof JsonPayload json) {
            payloadType = "JSON";
            payloadValue = json.json();
        }

        return new StudioMessageResponse(
                conversationId,
                result.intent(),
                result.state(),
                payloadType,
                payloadValue,
                result.contextJson()
        );
    }

    private StudioMessageResponse errorResponse(String conversationId, Exception ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Failed to process your request."
                : ex.getMessage();
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("error", true);
        ctx.put("errorType", ex.getClass().getSimpleName());
        ctx.put("errorMessage", message);
        ctx.put("publish_status", "FAILED");

        return new StudioMessageResponse(
                conversationId,
                StudioSessionKeys.MODE_MAPPING_STUDIO,
                "ERROR",
                "TEXT",
                "Request could not be completed: " + message,
                toJson(ctx)
        );
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ignored) {
            return "{\"error\":true}";
        }
    }
}
