package com.salilvnair.mapperstudio.llm.provider.openai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceResponse;
import lombok.Data;

import java.util.List;

@Data
public class OpenAiResponse implements RestWebServiceResponse {

    // ======================
    // Common
    // ======================
    private String id;
    private String object;
    private long created;
    private String model;

    // ======================
    // CHAT COMPLETIONS (OLD)
    // ======================
    private List<Choice> choices;

    // ======================
    // RESPONSES API (NEW)
    // ======================
    private List<OutputItem> output;

    private Usage usage;

    // ======================
    // Nested
    // ======================

    @Data
    public static class Choice {
        private Message message;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    // ---- Responses API ----

    @Data
    public static class OutputItem {
        private String id;
        private String type;
        private String role;
        private List<ContentBlock> content;
    }

    @Data
    public static class ContentBlock {
        private String type;
        private String text;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("completion_tokens")
        private int completionTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
    }

    // ======================
    // 🔥 SINGLE SAFE EXTRACTOR
    // ======================

    @JsonIgnore
    public String extractText() {

        // 1️⃣ Chat Completions (legacy, 4o / 4.0 / mini)
        if (choices != null && !choices.isEmpty()) {
            Message msg = choices.get(0).getMessage();
            if (msg != null && msg.getContent() != null) {
                return msg.getContent();
            }
        }

        // 2️⃣ Responses API (4.1 strict JSON)
        if (output != null && !output.isEmpty()) {
            OutputItem item = output.get(0);
            if (item.getContent() != null && !item.getContent().isEmpty()) {
                ContentBlock block = item.getContent().get(0);
                if (block.getText() != null) {
                    return block.getText();
                }
            }
        }

        return null;
    }
}
