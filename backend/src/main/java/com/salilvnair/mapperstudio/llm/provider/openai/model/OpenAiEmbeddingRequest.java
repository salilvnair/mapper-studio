package com.salilvnair.mapperstudio.llm.provider.openai.model;

import com.github.salilvnair.api.processor.rest.model.RestWebServiceRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAiEmbeddingRequest implements RestWebServiceRequest {
    private String model;
    private String input;
}
