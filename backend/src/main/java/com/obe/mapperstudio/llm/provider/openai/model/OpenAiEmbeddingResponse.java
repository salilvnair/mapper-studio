package com.obe.mapperstudio.llm.provider.openai.model;

import com.github.salilvnair.api.processor.rest.model.RestWebServiceResponse;
import lombok.Getter;

import java.util.List;

@Getter
public class OpenAiEmbeddingResponse implements RestWebServiceResponse {

    private List<Data> data;

    @Getter
    public static class Data {
        private List<Float> embedding;
    }
}
