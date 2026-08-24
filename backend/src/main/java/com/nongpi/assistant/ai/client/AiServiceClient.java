package com.nongpi.assistant.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.ai.config.AiServiceProperties;
import com.nongpi.assistant.ai.dto.AiActionResponse;
import com.nongpi.assistant.ai.dto.AiTranscribeResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.Map;

@Component
public class AiServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiServiceClient(AiServiceProperties properties,
                           ObjectMapper objectMapper,
                           RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        // HTTP/1.1：避免 JDK 客户端对 uvicorn/h11 发 Upgrade，导致偶发空/坏 body。
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        // 使用 Boot 注入的 Builder，自带 Jackson MessageConverter；裸 RestClient.builder() 发不出 JSON body。
        this.restClient = restClientBuilder
                .clone()
                .baseUrl(trimSlash(properties.baseUrl()))
                .requestFactory(factory)
                .build();
    }

    public AiActionResponse parseAction(Map<String, Object> body) {
        try {
            // 先压成 JSON 字节，避免 Map 内嵌 record 时部分 converter 序列化异常/空 body。
            byte[] json = objectMapper.writeValueAsBytes(body);
            JsonNode node = restClient.post()
                    .uri("/internal/ai/parse-action")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                throw new BusinessException(BusinessErrorCode.AI_UNAVAILABLE, "AI 服务返回空响应");
            }
            return objectMapper.convertValue(node, AiActionResponse.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(BusinessErrorCode.AI_UNAVAILABLE, "AI 请求序列化失败", Map.of(), ex);
        } catch (RestClientResponseException ex) {
            throw new BusinessException(
                    BusinessErrorCode.AI_UNAVAILABLE,
                    "AI 服务暂时不可用",
                    Map.of(
                            "aiStatus", ex.getStatusCode().value(),
                            "aiBody", truncate(ex.getResponseBodyAsString(), 800)
                    ),
                    ex
            );
        } catch (RestClientException ex) {
            throw new BusinessException(BusinessErrorCode.AI_UNAVAILABLE, "AI 服务暂时不可用", Map.of(), ex);
        }
    }

    public AiTranscribeResponse transcribe(byte[] audioBytes, String filename) {
        try {
            String safeName = (filename == null || filename.isBlank()) ? "audio.webm" : filename;
            ByteArrayResource fileResource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return safeName;
                }
            };
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", fileResource);
            JsonNode node = restClient.post()
                    .uri("/internal/ai/speech/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                throw new BusinessException(BusinessErrorCode.ASR_UNAVAILABLE, "ASR 服务返回空响应");
            }
            return objectMapper.convertValue(node, AiTranscribeResponse.class);
        } catch (RestClientResponseException ex) {
            throw new BusinessException(
                    BusinessErrorCode.ASR_UNAVAILABLE,
                    "语音识别暂时不可用",
                    Map.of(
                            "aiStatus", ex.getStatusCode().value(),
                            "aiBody", truncate(ex.getResponseBodyAsString(), 800)
                    ),
                    ex
            );
        } catch (RestClientException ex) {
            throw new BusinessException(BusinessErrorCode.ASR_UNAVAILABLE, "语音识别暂时不可用", Map.of(), ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
