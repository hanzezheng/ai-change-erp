package com.nongpi.assistant.erp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.erp.connection.ErpConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frappe / ERPNext REST 客户端。
 *
 * <p>这是系统中唯一发起 ERPNext HTTP 调用的地方（AGENTS.md #18）。
 * 上层只通过 Adapter 使用它，Controller 与业务 Service 不直接依赖本类。
 */
@Component
public class ErpRestClient {

    private static final String RESOURCE_PATH = "/api/resource/";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public ErpRestClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 查询列表。ERPNext 返回 {@code {"data": [...]}}。
     */
    public <T> List<T> list(ErpConnection connection, String doctype, ErpQuery query, Class<T> type) {
        String body = execute(connection, doctype, buildListUri(connection, doctype, query));
        return parseDataArray(doctype, body, type);
    }

    /**
     * 按主键读取单个文档。文档不存在返回 {@link Optional#empty()}，
     * 由调用方 Adapter 决定翻译成 CUSTOMER_NOT_FOUND 还是 ITEM_NOT_FOUND。
     */
    public <T> Optional<T> getDoc(ErpConnection connection, String doctype, String name, Class<T> type) {
        URI uri = URI.create(connection.baseUrl() + RESOURCE_PATH + encode(doctype) + "/" + encode(name));
        String body;
        try {
            body = clientFor(connection)
                    .get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, connection.authorizationHeader())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            throw ErpErrorTranslator.translate(doctype, ex);
        }
        return parseDataObject(doctype, body, type);
    }

    private String execute(ErpConnection connection, String doctype, URI uri) {
        try {
            return clientFor(connection)
                    .get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, connection.authorizationHeader())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException ex) {
            throw ErpErrorTranslator.translate(doctype, ex);
        }
    }

    private <T> List<T> parseDataArray(String doctype, String body, Class<T> type) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isArray()) {
                return List.of();
            }
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, type);
            return objectMapper.convertValue(data, listType);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw ErpErrorTranslator.translate(doctype, ex);
        }
    }

    private <T> Optional<T> parseDataObject(String doctype, String body, Class<T> type) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isObject()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.convertValue(data, type));
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw ErpErrorTranslator.translate(doctype, ex);
        }
    }

    private URI buildListUri(ErpConnection connection, String doctype, ErpQuery query) {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        if (!query.getFields().isEmpty()) {
            params.add(Map.entry("fields", writeJson(query.getFields())));
        }
        if (!query.getFilters().isEmpty()) {
            params.add(Map.entry("filters", writeJson(toJsonArrays(query.getFilters()))));
        }
        if (!query.getOrFilters().isEmpty()) {
            params.add(Map.entry("or_filters", writeJson(toJsonArrays(query.getOrFilters()))));
        }
        if (query.getOrderBy() != null) {
            params.add(Map.entry("order_by", query.getOrderBy()));
        }
        if (query.getLimitStart() != null) {
            params.add(Map.entry("limit_start", String.valueOf(query.getLimitStart())));
        }
        if (query.getLimitPageLength() != null) {
            params.add(Map.entry("limit_page_length", String.valueOf(query.getLimitPageLength())));
        }
        if (query.getParent() != null) {
            params.add(Map.entry("parent", query.getParent()));
        }

        StringBuilder uri = new StringBuilder(connection.baseUrl()).append(RESOURCE_PATH).append(encode(doctype));
        for (int i = 0; i < params.size(); i++) {
            uri.append(i == 0 ? '?' : '&')
                    .append(encode(params.get(i).getKey()))
                    .append('=')
                    .append(encode(params.get(i).getValue()));
        }
        return URI.create(uri.toString());
    }

    private List<List<Object>> toJsonArrays(List<ErpFilter> filters) {
        return filters.stream().map(ErpFilter::toJsonArray).toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化 ERPNext 查询参数", ex);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private RestClient clientFor(ErpConnection connection) {
        return clientCache.computeIfAbsent(connection.clientCacheKey(), key -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(connection.connectTimeout())
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(connection.readTimeout());
            return RestClient.builder().requestFactory(factory).build();
        });
    }
}
