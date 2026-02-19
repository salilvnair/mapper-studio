package com.obe.mapperstudio.llm.provider.lmstudio.delegate;

import com.github.salilvnair.api.processor.rest.handler.RestWebServiceDelegate;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceRequest;
import com.github.salilvnair.api.processor.rest.model.RestWebServiceResponse;
import com.obe.mapperstudio.llm.provider.openai.model.OpenAiEmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class LmStudioEmbeddingRestWebserviceDelegate implements RestWebServiceDelegate {

    @Value("${convengine.llm.openai.base-url}")
    private String baseUrl;

    @Override
    public RestWebServiceResponse invoke(
            RestWebServiceRequest request,
            Map<String, Object> map,
            Object... objects
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = new HttpEntity<>(request, headers);
        RestTemplate restTemplate = new RestTemplate();
        String apiUrl = baseUrl + "/v1/embeddings";
        ResponseEntity<OpenAiEmbeddingResponse> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, OpenAiEmbeddingResponse.class);
        return response.getBody();
    }
}
