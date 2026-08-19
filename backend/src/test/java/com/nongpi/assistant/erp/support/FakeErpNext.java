package com.nongpi.assistant.erp.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nongpi.assistant.erp.connection.ErpConnection;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 可控的 ERPNext 假服务。
 *
 * <p>测试不依赖真实 ERPNext 或公网：所有响应都是本地固定报文，
 * 便于覆盖字段缺失、无响应、错误状态码等分支。
 */
public final class FakeErpNext implements AutoCloseable {

    private static final String RESOURCE_PREFIX = "/api/resource/";

    /**
     * 真实 ERPNext 中存在的 DocType 名称。
     *
     * <p>Frappe 对未知 DocType 返回 404，对已知但无数据的 DocType 返回空 data。
     * Fake 必须区分这两种情况，否则路径编码错误、DocType 名拼错这类缺陷
     * 会被「反正返回空列表」掩盖过去。
     */
    private static final Set<String> KNOWN_DOCTYPES = Set.of(
            "Customer",
            "Item",
            "UOM Conversion Detail",
            "Item Variant Attribute",
            "Item Price",
            "Item Reorder",
            "Bin",
            "Sales Order",
            "Sales Order Item",
            "Payment Entry",
            "Payment Entry Reference",
            "Mode of Payment",
            "Mode of Payment Account",
            "Company"
    );

    private final MockWebServer server = new MockWebServer();
    private final Map<String, MockResponse> listResponses = new LinkedHashMap<>();
    private final Map<String, MockResponse> docResponses = new LinkedHashMap<>();
    private final List<RecordedRequest> requests = new ArrayList<>();
    private final FakeErpWriteEngine writeEngine = new FakeErpWriteEngine();
    private MockResponse fallback;

    public FakeErpNext() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requests.add(request);
                String path = decodedPath(request);
                MockResponse configured = docResponses.get(path);
                if (configured == null && "GET".equals(request.getMethod())) {
                    configured = doctypeOf(path).map(listResponses::get).orElse(null);
                }
                if (configured != null) {
                    return configured;
                }
                MockResponse written = writeEngine.handle(request);
                if (written != null) {
                    return written;
                }
                if (fallback != null) {
                    return fallback;
                }
                Optional<String> doctype = doctypeOf(path);
                if (doctype.isPresent() && !KNOWN_DOCTYPES.contains(doctype.get())) {
                    return doctypeNotFound(doctype.get());
                }
                return json(200, "{\"data\": []}");
            }
        });
    }

    public void start() throws IOException {
        server.start();
    }

    /**
     * 清空已配置的响应与已记录的请求，供跨测试复用同一个实例。
     */
    public void reset() {
        listResponses.clear();
        docResponses.clear();
        requests.clear();
        fallback = null;
        writeEngine.reset();
    }

    public FakeErpNext onList(String doctype, String jsonBody) {
        listResponses.put(doctype, json(200, jsonBody));
        return this;
    }

    public FakeErpNext onListStatus(String doctype, int status, String jsonBody) {
        listResponses.put(doctype, json(status, jsonBody));
        return this;
    }

    public FakeErpNext onDoc(String doctype, String name, String jsonBody) {
        docResponses.put(RESOURCE_PREFIX + doctype + "/" + name, json(200, jsonBody));
        return this;
    }

    public FakeErpNext onDocStatus(String doctype, String name, int status, String jsonBody) {
        docResponses.put(RESOURCE_PREFIX + doctype + "/" + name, json(status, jsonBody));
        return this;
    }

    /**
     * 模拟 ERPNext 无响应：连接可以建立，但迟迟不返回响应头，触发客户端读超时。
     *
     * <p>停顿时间只需明显超过测试用连接的读超时（500ms）。取太长会让 MockWebServer
     * 在测试结束时等待写线程收尾，拖慢整个测试套件。
     */
    public FakeErpNext hangOnEveryRequest() {
        this.fallback = new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS);
        return this;
    }

    public ErpConnection connection(String tenantId) {
        return connection(tenantId, "Standard Selling");
    }

    public ErpConnection connection(String tenantId, String sellingPriceList) {
        return new ErpConnection(
                tenantId,
                baseUrl(),
                "test-key",
                "test-secret",
                sellingPriceList,
                "主仓库 - T",
                "农批测试",
                Duration.ofMillis(500),
                Duration.ofMillis(500)
        );
    }

    public void hangNextWrite() {
        writeEngine.hangNextWrite();
    }

    public void setDocstatus(String doctype, String name, int docstatus, String status) {
        writeEngine.setDocstatus(doctype, name, docstatus, status);
    }

    public ObjectNode paymentDoc(String name) {
        return writeEngine.paymentDoc(name);
    }

    public void mutatePayment(String name, Consumer<ObjectNode> mutator) {
        writeEngine.mutatePayment(name, mutator);
    }

    public void seedUnrelatedPayments(int count) {
        writeEngine.seedUnrelatedPayments(count);
    }

    public String baseUrl() {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    /**
     * 针对某个 DocType 的第一个请求，用于断言真正下发给 ERPNext 的查询条件。
     */
    public Optional<RecordedRequest> firstRequestFor(String doctype) {
        return requests.stream()
                .filter(request -> doctypeOf(decodedPath(request)).filter(doctype::equals).isPresent())
                .findFirst();
    }

    /**
     * 请求的完整查询串（已 URL 解码），便于断言 fields / filters / parent 等参数。
     */
    public String decodedQueryFor(String doctype) {
        RecordedRequest request = firstRequestFor(doctype)
                .orElseThrow(() -> new AssertionError("没有针对 " + doctype + " 的请求"));
        String target = request.getPath() == null ? "" : request.getPath();
        int queryStart = target.indexOf('?');
        return queryStart < 0 ? "" : URLDecoder.decode(target.substring(queryStart + 1), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        server.shutdown();
    }

    /**
     * 按真实 HTTP 服务器的规则解码路径：只解 {@code %XX}，不把 {@code +} 当空格。
     *
     * <p>这一点必须和 Frappe 一致。之前这里用 {@link URLDecoder} 解码整个路径，
     * 会把 {@code +} 还原成空格，于是 {@code /api/resource/UOM+Conversion+Detail}
     * 被当成合法请求；真实 Frappe 会把它当成名叫 {@code UOM+Conversion+Detail}
     * 的 DocType 并返回 404。Fake 迁就客户端的错误编码会掩盖真实缺陷。
     */
    private static String decodedPath(RecordedRequest request) {
        String target = request.getPath() == null ? "" : request.getPath();
        int queryStart = target.indexOf('?');
        String path = queryStart < 0 ? target : target.substring(0, queryStart);
        return decodePercentEncoding(path);
    }

    private static String decodePercentEncoding(String value) {
        // 先把字面 '+' 保护起来，再做标准解码，避免 URLDecoder 把它变成空格。
        String guarded = value.replace("+", "%2B");
        return URLDecoder.decode(guarded, StandardCharsets.UTF_8);
    }

    private static Optional<String> doctypeOf(String decodedPath) {
        if (!decodedPath.startsWith(RESOURCE_PREFIX)) {
            return Optional.empty();
        }
        String remainder = decodedPath.substring(RESOURCE_PREFIX.length());
        int slash = remainder.indexOf('/');
        return Optional.of(slash < 0 ? remainder : remainder.substring(0, slash));
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }

    /**
     * 复刻真实 Frappe 对未知 DocType 的响应（实测 ERPNext v16 返回 404 + DoesNotExistError）。
     */
    private static MockResponse doctypeNotFound(String doctype) {
        return json(404, "{\"exc_type\": \"DoesNotExistError\", \"_server_messages\": "
                + "\"[\\\"{\\\\\\\"message\\\\\\\": \\\\\\\"DocType " + doctype + " not found\\\\\\\"}\\\"]\"}");
    }
}
