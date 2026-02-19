package com.obe.mapperstudio.service.studio;

import com.obe.mapperstudio.api.dto.StudioMessageRequest;
import com.obe.mapperstudio.api.dto.StudioMessageResponse;
import com.github.salilvnair.convengine.engine.context.EngineContext;
import com.github.salilvnair.convengine.engine.core.ConversationalEngine;
import com.github.salilvnair.convengine.engine.model.EngineResult;
import com.github.salilvnair.convengine.model.JsonPayload;
import com.github.salilvnair.convengine.model.OutputPayload;
import com.github.salilvnair.convengine.model.TextPayload;
import com.obe.mapperstudio.task.model.StudioSessionKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudioConversationService {

    private final ConversationalEngine engine;

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

        EngineResult result = engine.process(context);
        return toResponse(conversationId, result);
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
}
