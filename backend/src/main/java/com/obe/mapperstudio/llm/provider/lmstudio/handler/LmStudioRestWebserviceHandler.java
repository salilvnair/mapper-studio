package com.obe.mapperstudio.llm.provider.lmstudio.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.salilvnair.api.processor.rest.handler.RestWebServiceDelegate;
import com.github.salilvnair.api.processor.rest.handler.RestWebServiceHandler;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceRequest;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceResponse;
import com.obe.mapperstudio.llm.provider.lmstudio.context.LmStudioApiContext;
import com.obe.mapperstudio.llm.provider.lmstudio.delegate.LmStudioRestWebserviceDelegate;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiRequest;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LmStudioRestWebserviceHandler implements RestWebServiceHandler {

    private final LmStudioRestWebserviceDelegate delegate;
    private final ObjectMapper mapper;

    @Override
    public RestWebServiceDelegate delegate() {
        return delegate;
    }

    @Override
    public RestWebServiceRequest prepareRequest(Map<String, Object> map, Object... objects) {
        LmStudioApiContext ctx = (LmStudioApiContext) objects[0];

        OpenAiRequest req = new OpenAiRequest();
        req.setModel(ctx.getModel());

        List<OpenAiRequest.Message> messages = List.of(
                OpenAiRequest.Message.builder()
                        .role("system")
                        .content(ctx.getHint())
                        .build(),
                OpenAiRequest.Message.builder()
                        .role("user")
                        .content(ctx.getUserContext())
                        .build()
        );

        req.setMessages(messages);
        ctx.setMessages(messages);
        return req;
    }

    @Override
    public void processResponse(
            RestWebServiceRequest request,
            RestWebServiceResponse response,
            Map<String, Object> map,
            Object... objects
    ) {
        LmStudioApiContext ctx = (LmStudioApiContext) objects[0];
        ctx.setResponse((OpenAiResponse) response);
    }

    @Override
    public String webServiceName() {
        return "LmStudioChatCompletion";
    }
}