package com.obe.mapperstudio.llm.provider.lmstudio.handler;

import com.github.salilvnair.api.processor.rest.handler.RestWebServiceDelegate;
import com.github.salilvnair.api.processor.rest.handler.RestWebServiceHandler;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceRequest;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceResponse;
import com.obe.mapperstudio.llm.provider.lmstudio.context.LmStudioEmbeddingApiContext;
import com.obe.mapperstudio.llm.provider.lmstudio.delegate.LmStudioEmbeddingRestWebserviceDelegate;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiEmbeddingRequest;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LmStudioEmbeddingRestWebserviceHandler implements RestWebServiceHandler {

    private final LmStudioEmbeddingRestWebserviceDelegate delegate;

    @Override
    public RestWebServiceDelegate delegate() {
        return delegate;
    }

    @Override
    public RestWebServiceRequest prepareRequest(
            Map<String, Object> restWsMap,
            Object... objects
    ) {
        LmStudioEmbeddingApiContext ctx = (LmStudioEmbeddingApiContext) objects[0];

        OpenAiEmbeddingRequest req = new OpenAiEmbeddingRequest();
        req.setModel(ctx.getModel());
        req.setInput(ctx.getInput());

        return req;
    }

    @Override
    public void processResponse(
            RestWebServiceRequest request,
            RestWebServiceResponse response,
            Map<String, Object> restWsMap,
            Object... objects
    ) {
        LmStudioEmbeddingApiContext ctx = (LmStudioEmbeddingApiContext) objects[0];
        OpenAiEmbeddingResponse resp = (OpenAiEmbeddingResponse) response;

        if (resp.getData() == null || resp.getData().isEmpty()) {
            throw new IllegalStateException("OpenAI embedding response has no data");
        }

        List<Float> embeddingList = resp.getData().get(0).getEmbedding();
        if (embeddingList == null || embeddingList.isEmpty()) {
            throw new IllegalStateException("OpenAI embedding vector is empty");
        }

        float[] embedding = new float[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            embedding[i] = embeddingList.get(i);
        }

        // 🔒 Safety check — prevents silent corruption
        float norm = 0f;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm < 0.01f) {
            throw new IllegalStateException("Embedding vector is near-zero — something is broken");
        }

        ctx.setEmbedding(embedding);
    }

    @Override
    public String webServiceName() {
        return "OpenAiEmbedding";
    }
}
