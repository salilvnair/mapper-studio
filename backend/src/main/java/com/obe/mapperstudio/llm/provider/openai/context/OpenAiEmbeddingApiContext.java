package com.obe.mapperstudio.llm.provider.openai.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OpenAiEmbeddingApiContext {

    private String model;
    private String input;

    // output
    private float[] embedding;
}
