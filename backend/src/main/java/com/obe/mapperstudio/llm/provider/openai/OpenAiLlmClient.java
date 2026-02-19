package com.obe.mapperstudio.llm.provider.openai;

import com.github.salilvnair.api.processor.rest.facade.RestWebServiceFacade;
import com.github.salilvnair.convengine.entity.CeLlmCallLog;
import com.github.salilvnair.convengine.llm.base.type.OutputType;
import com.github.salilvnair.convengine.llm.context.LlmInvocationContext;
import com.github.salilvnair.convengine.llm.core.LlmClient;
import com.github.salilvnair.convengine.repo.LlmCallLogRepository;
import com.obe.mapperstudio.llm.provider.openai.context.OpenAiApiContext;
import com.obe.mapperstudio.llm.provider.openai.context.OpenAiEmbeddingApiContext;
import com.obe.mapperstudio.llm.provider.openai.handler.OpenAiEmbeddingRestWebserviceHandler;
import com.obe.mapperstudio.llm.provider.openai.handler.OpenAiRestWebserviceHandler;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiRequest;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
        name = "convengine.llm.provider",
        havingValue = "openai",
        matchIfMissing = true
)
public class OpenAiLlmClient implements LlmClient {
    private final LlmCallLogRepository logRepo;
    private final RestWebServiceFacade restWebServiceFacade;
    private final OpenAiRestWebserviceHandler handler;
    private final OpenAiEmbeddingRestWebserviceHandler embeddingHandler;


    @Value("${convengine.llm.provider}")
    private String provider;
    @Value("${convengine.llm.temperature}")
    private String temperature;
    @Value("${convengine.llm.openai.model}")
    private String model;

    public double temperature() {
        try {
            return Double.parseDouble(temperature);
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public String generateText(String hint, String context) {
        OpenAiApiContext apiContext = OpenAiApiContext
                                        .builder()
                                        .model(model)
                                        .temperature(temperature())
                                        .hint(hint)
                                        .userContext(context)
                                        .type(OutputType.TEXT)
                                        .build();
        return callLlm(apiContext);
    }

    @Override
    public String generateJson(String hint, String jsonSchema, String context) {
        OpenAiApiContext apiContext = OpenAiApiContext
                                        .builder()
                                        .model(model)
                                        .temperature(temperature())
                                        .hint(hint)
                                        .userContext(context)
                                        .jsonSchema(jsonSchema)
                                        .type(OutputType.JSON)
                                        .build();
        return callLlm(apiContext);
    }

    @Override
    public float[] generateEmbedding(String input) {
        OpenAiEmbeddingApiContext ctx = OpenAiEmbeddingApiContext.builder()
                .model("text-embedding-3-small")
                .input(input)
                .build();

        restWebServiceFacade.initiate(embeddingHandler, new HashMap<>(), ctx);

        return ctx.getEmbedding();
    }

    private String callLlm(OpenAiApiContext apiContext) {
        LlmInvocationContext ctx = LlmInvocationContext.get();
        CeLlmCallLog log = CeLlmCallLog.builder()
                .conversationId(
                        ctx != null ? ctx.conversationId() : null
                )
                .intentCode(
                        ctx != null ? ctx.intent() : null
                )
                .stateCode(
                        ctx != null ? ctx.state() : null
                )
                .provider(provider)
                .model(model)
                .temperature(temperature())
                .userContext(apiContext.getUserContext())
                .success(false)
                .createdAt(OffsetDateTime.now())
                .build();

        try {
            restWebServiceFacade.initiate(handler, new HashMap<>(), apiContext);
            String prompt = flattenPrompt(apiContext.getMessages());
            log.setPromptText(prompt == null ? "" : prompt);
            OpenAiResponse response = apiContext.getResponse();
            String content = response.extractText();
            log.setResponseText(content);
            log.setSuccess(true);
            return content;

        } catch (Exception e) {
            String prompt = flattenPrompt(apiContext.getMessages());
            log.setPromptText(prompt == null ? "" : prompt);
            log.setErrorMessage(e.getMessage());
            throw new IllegalStateException("OpenAI call failed", e);

        } finally {
            logRepo.save(log);
        }
    }

    private String flattenPrompt(List<OpenAiRequest.Message> messages) {
        return messages.stream()
                .map(m -> "[" + m.getRole() + "] " + m.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    @Override
    public String generateJsonStrict(String hint, String jsonSchema, String context) {
        OpenAiApiContext apiContext =
                OpenAiApiContext.builder()
                        .model(model)
                        .temperature(0.0)
                        .hint(hint)
                        .userContext(context)
                        .jsonSchema(jsonSchema)
                        .type(OutputType.JSON)
                        .strictJson(true)
                        .build();

        return callLlm(apiContext);
    }

}
