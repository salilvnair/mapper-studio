package com.obe.mapperstudio.llm.provider.lmstudio.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LmStudioEmbeddingApiContext {

    private String model;
    private String input;

    // output
    private float[] embedding;
}
