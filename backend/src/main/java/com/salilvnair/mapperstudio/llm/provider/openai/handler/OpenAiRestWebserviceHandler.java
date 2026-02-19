package com.salilvnair.mapperstudio.llm.provider.openai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.salilvnair.api.processor.rest.handler.RestWebServiceDelegate;
import com.github.salilvnair.api.processor.rest.handler.RestWebServiceHandler;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceRequest;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceResponse;
import com.salilvnair.mapperstudio.llm.provider.openai.context.OpenAiApiContext;
import com.salilvnair.mapperstudio.llm.provider.openai.delegate.OpenAiRestWebserviceDelegate;
import com.salilvnair.mapperstudio.llm.provider.openai.model.OpenAiRequest;
import com.salilvnair.mapperstudio.llm.provider.openai.model.OpenAiResponse;
import com.github.salilvnair.convengine.llm.base.type.OutputType;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class OpenAiRestWebserviceHandler implements RestWebServiceHandler {

    private final OpenAiRestWebserviceDelegate delegate;
    private final ObjectMapper mapper;

    @Override
    public RestWebServiceDelegate delegate() {
        return delegate;
    }

    @SneakyThrows
    @Override
    public RestWebServiceRequest prepareRequest(
            Map<String, Object> restWsMap,
            Object... objects
    ) {
        OpenAiApiContext ctx = (OpenAiApiContext) objects[0];

        OpenAiRequest req = new OpenAiRequest();
        req.setModel(ctx.getModel());
        req.setTemperature(ctx.getTemperature());

        // ----------------------------
        // TEXT MODE (always non-strict)
        // ----------------------------
        if (OutputType.TEXT.equals(ctx.getType())) {

            List<OpenAiRequest.Message> messages =
                    buildTextMessages(ctx.getHint(), ctx.getUserContext());

            req.setMessages(messages);
            ctx.setMessages(messages);
            return req;
        }

        // ----------------------------
        // JSON MODE
        // ----------------------------
        if (OutputType.JSON.equals(ctx.getType())) {

            List<OpenAiRequest.Message> messages = buildJsonMessages(ctx);

            // 🔒 STRICT JSON → Responses API
            if (ctx.isStrictJson()) {
                req.setInput(messages);

                OpenAiRequest.Format format = new OpenAiRequest.Format();
                format.setName("output");
                format.setType("json_schema");
                format.setSchema(mapper.readValue(ctx.getJsonSchema(), Object.class));
                format.setStrict(true);

                OpenAiRequest.Text text = new OpenAiRequest.Text();
                text.setFormat(format);

                req.setText(text);
                ctx.setMessages(messages);
                return req;
            }

            // 🟡 NON-STRICT JSON (Chat-style, backward compatible)
            req.setMessages(messages);
            ctx.setMessages(messages);
            return req;
        }

        throw new IllegalStateException("Unsupported OutputType: " + ctx.getType());
    }

    @Override
    public void processResponse(
            RestWebServiceRequest request,
            RestWebServiceResponse response,
            Map<String, Object> restWsMap,
            Object... objects
    ) {
        OpenAiApiContext ctx = (OpenAiApiContext) objects[0];
        ctx.setResponse((OpenAiResponse) response);
    }

    // ------------------------------------------------------------------
    // Message builders
    // ------------------------------------------------------------------

    private List<OpenAiRequest.Message> buildTextMessages(
            String hint,
            String userText
    ) {
        return List.of(
                OpenAiRequest.Message.builder()
                        .role("system")
                        .content("You are a concise conversational assistant.")
                        .build(),
                OpenAiRequest.Message.builder()
                        .role("system")
                        .content(hint)
                        .build(),
                OpenAiRequest.Message.builder()
                        .role("user")
                        .content(userText)
                        .build()
        );
    }

    private List<OpenAiRequest.Message> buildJsonMessages( OpenAiApiContext ctx ) {
        String hint = ctx.getHint();
        String jsonSchema = ctx.getJsonSchema();
        return List.of(
                OpenAiRequest.Message.builder()
                        .role("system")
                        .content("""
                                You are a JSON extraction engine.
                                You MUST return ONLY valid JSON.
                                Do NOT add explanations.
                                Do NOT use markdown.
                                """)
                        .build(),
                OpenAiRequest.Message.builder()
                        .role("system")
                        .content("JSON Schema:\n" + jsonSchema)
                        .build(),
                OpenAiRequest.Message.builder()
                        .role("system")
                        .content(hint)
                        .build());
    }

    @Override
    public String webServiceName() {
        return "OpenAiChatCompletion";
    }
}
