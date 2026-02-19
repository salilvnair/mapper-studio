package com.salilvnair.mapperstudio.llm.provider.openai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiRequest implements RestWebServiceRequest {

    private String model;
    private Double temperature;

    // CHAT COMPLETIONS
    private List<Message> messages;

    // RESPONSES API
    private List<Message> input;

    /**
     * Responses API output control
     */
    @JsonProperty("text")
    private Text text;

    // ------------------------------------------------------------------

    @Getter
    @Setter
    public static class Text {
        @JsonProperty("format")
        private Format format;
    }

    @Getter
    @Setter
    public static class Format {
        private String type;
        private String name;
        private Object schema;
        private boolean strict;
    }


    // ------------------------------------------------------------------

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
