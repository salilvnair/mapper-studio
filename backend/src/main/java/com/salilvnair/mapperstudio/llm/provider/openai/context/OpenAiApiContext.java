package com.salilvnair.mapperstudio.llm.provider.openai.context;

import com.github.salilvnair.convengine.llm.base.type.OutputType;
import com.salilvnair.mapperstudio.llm.provider.openai.model.OpenAiRequest;
import com.salilvnair.mapperstudio.llm.provider.openai.model.OpenAiResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OpenAiApiContext {
    private String model;
    private Double temperature;
    private String hint;
    private String userContext;
    private String jsonSchema;
    private OutputType type;
    private OpenAiResponse response;
    private List<OpenAiRequest.Message> messages;
    private boolean strictJson;
}
