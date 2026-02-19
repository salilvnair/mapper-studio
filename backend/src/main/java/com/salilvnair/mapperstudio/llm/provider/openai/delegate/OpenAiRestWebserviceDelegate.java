package com.salilvnair.mapperstudio.llm.provider.openai.delegate;

import com.github.salilvnair.api.processor.rest.handler.RestWebServiceDelegate;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceRequest;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceResponse;
import com.salilvnair.mapperstudio.llm.provider.openai.context.OpenAiApiContext;
import com.salilvnair.mapperstudio.llm.provider.openai.model.OpenAiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class OpenAiRestWebserviceDelegate implements RestWebServiceDelegate {

    @Value("${convengine.llm.openai.api-key}")
    private String apiKey;
    @Value("${convengine.llm.openai.base-url}")
    private String baseUrl;

    @Override
    public RestWebServiceResponse invoke(RestWebServiceRequest restWebServiceRequest, Map<String, Object> map, Object... objects) {
        OpenAiApiContext ctx = (OpenAiApiContext) objects[0];
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");
        HttpEntity<?> requestEntity = new HttpEntity<>(restWebServiceRequest, headers);
        RestTemplate restTemplate = new RestTemplate();
        String apiUrl = baseUrl + (ctx.isStrictJson() ? "/v1/responses" : "/v1/chat/completions");
        ResponseEntity<OpenAiResponse> responseEntity = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, OpenAiResponse.class);
        return responseEntity.getBody();
    }
}
