package com.nongpi.assistant.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.ai.config.AiServiceProperties;
import com.nongpi.assistant.ai.dto.AiActionResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.Map;

@Component
public class AiServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiServiceClient(AiServiceProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(trimSlash(properties.baseUrl()))
                .requestFactory(factory)
                .build();
    }

    public AiActionResponse parseAction(Map<String, Object> body) {
        try {
            JsonNode node = restClient.post()
                    .uri("/internal/ai/parse-action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                throw new BusinessException(BusinessErrorCode.AI_UNAVAILABLE, "AI 服务返回空响应");
            }
            return objectMapper.convertValue(node, AiActionResponse.class);
        } catch (RestClientException ex) {
            throw new BusinessException(BusinessErrorCode.AI_UNAVAILABLE, "AI 服务暂时不可用", Map.of(), ex);
        }
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
