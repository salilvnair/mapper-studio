package com.obe.mapperstudio.llm.provider.lmstudio.context;

import com.obe.mapperstudio.llm.provider.openai.model.OpenAiRequest;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiResponse;
import com.github.salilvnair.convengine.llm.base.type.OutputType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LmStudioApiContext {
    private String model;
    private String hint;
    private String userContext;
    private String jsonSchema;
    private OutputType type;
    private boolean strictJson;

    private OpenAiResponse response;
    private List<OpenAiRequest.Message> messages;
}