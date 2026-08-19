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
        return getDocNode(connection, doctype, name).map(node -> convert(doctype, node, type));
    }

    /**
     * 读取文档原始 JSON。写链路需要保留 ERPNext 子表与 modified 等未建模字段。
     */
    public Optional<JsonNode> getDocNode(ErpConnection connection, String doctype, String name) {
        URI uri = documentUri(connection, doctype, name);
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
        return parseDataNode(doctype, body);
    }

    /**
     * 创建文档。对应 {@code POST /api/resource/{Doctype}}。
     * 业务差异（Sales Order vs Payment Entry）必须留在 Adapter，不在本方法里分支。
     */
    public JsonNode createDoc(ErpConnection connection, String doctype, Object document) {
        URI uri = URI.create(connection.baseUrl() + RESOURCE_PATH + encodePathSegment(doctype));
        String body = executeWrite(connection, doctype, () -> clientFor(connection)
                .post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, connection.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(writeJson(document))
                .retrieve()
                .body(String.class));
        return parseDataNode(doctype, body).orElseThrow(() -> ErpErrorTranslator.translate(doctype,
                new IllegalStateException("ERPNext 创建文档未返回 data")));
    }

    /**
     * 更新文档。对应 {@code PUT /api/resource/{Doctype}/{name}}。
     */
    public JsonNode updateDoc(ErpConnection connection, String doctype, String name, Object document) {
        URI uri = documentUri(connection, doctype, name);
        String body = executeWrite(connection, doctype, () -> clientFor(connection)
                .put()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, connection.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(writeJson(document))
                .retrieve()
                .body(String.class));
        return parseDataNode(doctype, body).orElseThrow(() -> ErpErrorTranslator.translate(doctype,
                new IllegalStateException("ERPNext 更新文档未返回 data")));
    }

    /**
     * 调用 Frappe RPC。对应 {@code POST /api/method/{method}}。
     * 用于 submit、get_payment_entry 等标准方法，不按业务拆 HTTP 方法。
     */
    public JsonNode callMethod(ErpConnection connection, String method, Object args) {
        URI uri = URI.create(connection.baseUrl() + "/api/method/" + method);
        String body = executeWrite(connection, method, () -> clientFor(connection)
                .post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, connection.authorizationHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(writeJson(args == null ? Map.of() : args))
                .retrieve()
                .body(String.class));
        return parseMessageNode(method, body);
    }

    /**
     * 提交已存在文档。优先 {@code frappe.client.submit}，由 Adapter 传入完整 doc。
     */
    public JsonNode submitDoc(ErpConnection connection, JsonNode doc) {
        String doctype = doc.path("doctype").asText("Document");
        JsonNode submitted = callMethod(connection, "frappe.client.submit", Map.of("doc", toMap(doc)));
        if (submitted.isMissingNode() || submitted.isNull()) {
            throw ErpErrorTranslator.translate(doctype, new IllegalStateException("ERPNext submit 未返回文档"));
        }
        return submitted;
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

    private String executeWrite(ErpConnection connection, String doctype, java.util.function.Supplier<String> call) {
        try {
            return call.get();
        } catch (RuntimeException ex) {
            throw ErpErrorTranslator.translate(doctype, ex);
        }
    }

    private URI documentUri(ErpConnection connection, String doctype, String name) {
        return URI.create(connection.baseUrl() + RESOURCE_PATH
                + encodePathSegment(doctype) + "/" + encodePathSegment(name));
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

    private Optional<JsonNode> parseDataNode(String doctype, String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            return data.isObject() ? Optional.of(data) : Optional.empty();
        } catch (JsonProcessingException ex) {
            throw ErpErrorTranslator.translate(doctype, ex);
        }
    }

    private JsonNode parseMessageNode(String method, String body) {
        if (body == null || body.isBlank()) {
            throw ErpErrorTranslator.translate(method, new IllegalStateException("ERPNext 方法返回空响应"));
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.get("message");
            return message == null || message.isNull() || message.isMissingNode() ? root : message;
        } catch (JsonProcessingException ex) {
            throw ErpErrorTranslator.translate(method, ex);
        }
    }

    private <T> T convert(String doctype, JsonNode node, Class<T> type) {
        try {
            return objectMapper.convertValue(node, type);
        } catch (IllegalArgumentException ex) {
            throw ErpErrorTranslator.translate(doctype, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
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

        StringBuilder uri = new StringBuilder(connection.baseUrl())
                .append(RESOURCE_PATH)
                .append(encodePathSegment(doctype));
        for (int i = 0; i < params.size(); i++) {
            uri.append(i == 0 ? '?' : '&')
                    .append(encodeQueryValue(params.get(i).getKey()))
                    .append('=')
                    .append(encodeQueryValue(params.get(i).getValue()));
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

    /**
     * 编码 URL 路径片段。
     *
     * <p>路径中的空格必须是 {@code %20}：{@code +} 在路径里是字面加号，不代表空格。
     * ERPNext 很多 DocType 名字带空格（{@code UOM Conversion Detail}、{@code Item Price}
     * 等），用 {@code +} 会让 Frappe 把 DocType 名当成 {@code UOM+Conversion+Detail}
     * 并返回 404。
     */
    static String encodePathSegment(String value) {
        return encodeForm(value).replace("+", "%20");
    }

    /**
     * 编码查询参数。查询串采用 form 编码，空格用 {@code +} 是合法的。
     */
    static String encodeQueryValue(String value) {
        return encodeForm(value);
    }

    private static String encodeForm(String value) {
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
