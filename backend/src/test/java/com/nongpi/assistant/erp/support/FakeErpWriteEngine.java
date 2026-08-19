package com.nongpi.assistant.erp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake ERPNext 的 Sales Order / Payment Entry 写引擎。只覆盖 Phase 3 测试需要的标准行为。
 */
final class FakeErpWriteEngine {

    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ObjectNode> salesOrders = new LinkedHashMap<>();
    private final Map<String, ObjectNode> payments = new LinkedHashMap<>();
    private final List<ObjectNode> paymentMethods = new ArrayList<>();
    private final List<ObjectNode> paymentMethodAccounts = new ArrayList<>();
    private final AtomicInteger orderSeq = new AtomicInteger(1);
    private final AtomicInteger paymentSeq = new AtomicInteger(1);
    private final AtomicInteger childSeq = new AtomicInteger(1);
    private LocalDateTime clock = LocalDateTime.of(2026, 8, 19, 12, 0, 0);
    private boolean hangNextWrite;

    FakeErpWriteEngine() {
        seedPaymentMethods();
    }

    void reset() {
        salesOrders.clear();
        payments.clear();
        paymentMethods.clear();
        paymentMethodAccounts.clear();
        orderSeq.set(1);
        paymentSeq.set(1);
        childSeq.set(1);
        clock = LocalDateTime.of(2026, 8, 19, 12, 0, 0);
        hangNextWrite = false;
        seedPaymentMethods();
    }

    void hangNextWrite() {
        hangNextWrite = true;
    }

    void setDocstatus(String doctype, String name, int docstatus, String status) {
        ObjectNode stored = document(doctype, name);
        if (stored != null) {
            stored.put("docstatus", docstatus);
            if (status != null) {
                stored.put("status", status);
            }
        }
    }

    MockResponse handle(RecordedRequest request) {
        if (hangNextWrite && ("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()))) {
            hangNextWrite = false;
            return new MockResponse().setHeadersDelay(2, java.util.concurrent.TimeUnit.SECONDS);
        }
        String path = path(request);
        String method = request.getMethod();
        if ("POST".equals(method) && path.startsWith("/api/method/")) {
            return handleMethod(path.substring("/api/method/".length()), body(request));
        }
        if (path.startsWith("/api/resource/")) {
            String remainder = path.substring("/api/resource/".length());
            int slash = remainder.indexOf('/');
            String doctype = decode(slash < 0 ? remainder : remainder.substring(0, slash));
            String name = slash < 0 ? null : decode(remainder.substring(slash + 1));
            if ("POST".equals(method) && name == null) {
                return create(doctype, body(request));
            }
            if ("PUT".equals(method) && name != null) {
                return update(doctype, name, body(request));
            }
            if ("GET".equals(method) && name != null) {
                return get(doctype, name);
            }
            if ("GET".equals(method)) {
                return list(doctype, request.getPath());
            }
        }
        return null;
    }

    private MockResponse handleMethod(String method, JsonNode args) {
        if ("frappe.client.submit".equals(method)) {
            JsonNode doc = args.path("doc");
            String doctype = doc.path("doctype").asText();
            String name = doc.path("name").asText();
            ObjectNode stored = document(doctype, name);
            if (stored == null) {
                return json(404, "{\"exc_type\":\"DoesNotExistError\"}");
            }
            stored.put("docstatus", 1);
            if (ErpSalesOrder.equals(doctype)) {
                stored.put("status", "To Deliver and Bill");
            }
            if (ErpPayment.equals(doctype)) {
                applyAdvance(stored);
            }
            touch(stored);
            return message(stored);
        }
        if ("erpnext.accounts.doctype.payment_entry.payment_entry.get_payment_entry".equals(method)) {
            String orderId = args.path("dn").asText();
            ObjectNode order = salesOrders.get(orderId);
            if (order == null) {
                return json(404, "{\"exc_type\":\"DoesNotExistError\"}");
            }
            BigDecimal amount = decimal(args.path("party_amount"), order.path("grand_total").decimalValue());
            ObjectNode pe = MAPPER.createObjectNode();
            pe.put("doctype", ErpPayment);
            pe.put("payment_type", "Receive");
            pe.put("docstatus", 0);
            pe.put("company", order.path("company").asText());
            pe.put("party_type", "Customer");
            pe.put("party", order.path("customer").asText());
            pe.put("party_name", order.path("customer_name").asText());
            pe.put("paid_from", "Debtors - NPT");
            pe.put("paid_to", "Cash - NPT");
            pe.put("paid_amount", amount);
            pe.put("received_amount", amount);
            pe.put("difference_amount", 0);
            ArrayNode refs = pe.putArray("references");
            ObjectNode ref = refs.addObject();
            ref.put("reference_doctype", ErpSalesOrder);
            ref.put("reference_name", orderId);
            ref.put("allocated_amount", amount);
            ref.put("total_amount", amount);
            ref.put("outstanding_amount", amount);
            return message(pe);
        }
        return json(404, "{\"exc_type\":\"DoesNotExistError\"}");
    }

    private MockResponse create(String doctype, JsonNode body) {
        if (ErpSalesOrder.equals(doctype)) {
            ObjectNode order = MAPPER.createObjectNode();
            String name = "SAL-ORD-" + String.format("%05d", orderSeq.getAndIncrement());
            order.put("name", name);
            order.put("doctype", ErpSalesOrder);
            order.put("customer", body.path("customer").asText());
            order.put("customer_name", body.path("customer").asText());
            order.put("company", body.path("company").asText());
            order.put("transaction_date", textOr(body, "transaction_date", LocalDate.now().toString()));
            order.put("delivery_date", textOr(body, "delivery_date", order.path("transaction_date").asText()));
            order.put("currency", "CNY");
            order.put("status", "Draft");
            order.put("docstatus", 0);
            order.put("advance_paid", 0);
            order.set("items", copyItems(body.path("items"), name));
            recalc(order);
            stampNew(order);
            salesOrders.put(name, order);
            return data(order);
        }
        if (ErpPayment.equals(doctype)) {
            ObjectNode pe = body.deepCopy();
            if (!(pe instanceof ObjectNode node)) {
                return json(400, "{\"exc_type\":\"ValidationError\"}");
            }
            String name = "ACC-PAY-" + String.format("%05d", paymentSeq.getAndIncrement());
            node.put("name", name);
            node.put("doctype", ErpPayment);
            node.put("docstatus", 0);
            node.put("difference_amount", 0);
            if (!node.hasNonNull("party_name")) {
                node.put("party_name", node.path("party").asText());
            }
            stampNew(node);
            payments.put(name, node);
            return data(node);
        }
        return null;
    }

    private MockResponse update(String doctype, String name, JsonNode body) {
        ObjectNode stored = document(doctype, name);
        if (stored == null) {
            return json(404, "{\"exc_type\":\"DoesNotExistError\"}");
        }
        if (stored.path("docstatus").asInt() != 0) {
            return json(417, "{\"exc_type\":\"ValidationError\",\"exception\":\"Cannot edit submitted document\"}");
        }
        if (body.hasNonNull("modified") && !body.path("modified").asText().equals(stored.path("modified").asText())) {
            return json(417, "{\"exception\":\"frappe.exceptions.TimestampMismatchError: modified\","
                    + "\"exc_type\":\"TimestampMismatchError\"}");
        }
        if (ErpSalesOrder.equals(doctype)) {
            stored.put("customer", textOr(body, "customer", stored.path("customer").asText()));
            stored.put("customer_name", stored.path("customer").asText());
            stored.put("transaction_date", textOr(body, "transaction_date", stored.path("transaction_date").asText()));
            stored.set("items", copyItems(body.path("items"), name));
            recalc(stored);
            touch(stored);
            return data(stored);
        }
        if (ErpPayment.equals(doctype)) {
            stored.setAll((ObjectNode) body);
            stored.put("name", name);
            touch(stored);
            return data(stored);
        }
        return null;
    }

    private MockResponse get(String doctype, String name) {
        ObjectNode stored = document(doctype, name);
        return stored == null ? null : data(stored);
    }

    private MockResponse list(String doctype, String rawPath) {
        List<ObjectNode> rows = switch (doctype) {
            case ErpSalesOrder -> new ArrayList<>(salesOrders.values());
            case ErpSalesOrderItem -> flattenItems();
            case ErpPayment -> new ArrayList<>(payments.values());
            case "Mode of Payment" -> new ArrayList<>(paymentMethods);
            case "Mode of Payment Account" -> new ArrayList<>(paymentMethodAccounts);
            default -> null;
        };
        if (rows == null) {
            return null;
        }
        List<Filter> filters = parseFilters(rawPath, "filters");
        List<Filter> orFilters = parseFilters(rawPath, "or_filters");
        rows = rows.stream().filter(row -> matchesAnd(row, filters) && matchesOr(row, orFilters)).toList();
        rows = sort(rows, stringParam(rawPath, "order_by"));
        int start = intParam(rawPath, "limit_start", 0);
        int len = intParam(rawPath, "limit_page_length", 20);
        if (len == 0) {
            len = rows.size();
        }
        int end = Math.min(start + len, rows.size());
        ArrayNode data = MAPPER.createArrayNode();
        rows.subList(Math.min(start, rows.size()), end).forEach(data::add);
        ObjectNode body = MAPPER.createObjectNode();
        body.set("data", data);
        return json(200, body.toString());
    }

    private void applyAdvance(ObjectNode payment) {
        JsonNode refs = payment.path("references");
        if (!refs.isArray()) {
            return;
        }
        for (JsonNode ref : refs) {
            if (!ErpSalesOrder.equals(ref.path("reference_doctype").asText())) {
                continue;
            }
            ObjectNode order = salesOrders.get(ref.path("reference_name").asText());
            if (order == null) {
                continue;
            }
            BigDecimal paid = order.path("advance_paid").decimalValue()
                    .add(ref.path("allocated_amount").decimalValue());
            order.put("advance_paid", paid);
        }
    }

    private ArrayNode copyItems(JsonNode items, String parent) {
        ArrayNode copied = MAPPER.createArrayNode();
        if (!items.isArray()) {
            return copied;
        }
        int idx = 1;
        for (JsonNode item : items) {
            ObjectNode row = MAPPER.createObjectNode();
            String name = item.path("name").asText(null);
            if (name == null || name.isBlank()) {
                name = "row-" + childSeq.getAndIncrement();
            }
            row.put("name", name);
            row.put("parent", parent);
            row.put("parenttype", ErpSalesOrder);
            row.put("parentfield", "items");
            row.put("item_code", item.path("item_code").asText());
            row.put("item_name", itemName(item.path("item_code").asText()));
            BigDecimal qty = decimal(item.path("qty"), BigDecimal.ZERO);
            BigDecimal rate = decimal(item.path("rate"), BigDecimal.ZERO);
            String uom = item.path("uom").asText();
            row.put("qty", qty);
            row.put("uom", uom);
            row.put("rate", rate);
            row.put("amount", qty.multiply(rate).setScale(2, RoundingMode.HALF_UP));
            row.put("conversion_factor", "斤".equals(uom) ? 20 : 1);
            row.put("idx", idx++);
            copied.add(row);
        }
        return copied;
    }

    private void recalc(ObjectNode order) {
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode item : order.path("items")) {
            total = total.add(item.path("amount").decimalValue());
        }
        order.put("total", total);
        order.put("grand_total", total);
    }

    private List<ObjectNode> flattenItems() {
        List<ObjectNode> rows = new ArrayList<>();
        for (ObjectNode order : salesOrders.values()) {
            for (JsonNode item : order.path("items")) {
                rows.add((ObjectNode) item);
            }
        }
        return rows;
    }

    private ObjectNode document(String doctype, String name) {
        if (ErpSalesOrder.equals(doctype)) {
            return salesOrders.get(name);
        }
        if (ErpPayment.equals(doctype)) {
            return payments.get(name);
        }
        return null;
    }

    private void seedPaymentMethods() {
        paymentMethods.add(method("微信", "Cash"));
        paymentMethods.add(method("未配置", "Bank"));
        ObjectNode account = MAPPER.createObjectNode();
        account.put("name", "mop-wechat-npt");
        account.put("parent", "微信");
        account.put("parenttype", "Mode of Payment");
        account.put("parentfield", "accounts");
        account.put("company", "农批测试");
        account.put("default_account", "Cash - NPT");
        paymentMethodAccounts.add(account);
    }

    private ObjectNode method(String name, String type) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", name);
        node.put("mode_of_payment", name);
        node.put("type", type);
        node.put("enabled", 1);
        return node;
    }

    private void stampNew(ObjectNode node) {
        String now = now();
        node.put("creation", now);
        node.put("modified", now);
    }

    private void touch(ObjectNode node) {
        node.put("modified", now());
    }

    private String now() {
        clock = clock.plusNanos(1_000_000);
        return clock.format(MODIFIED);
    }

    private static String itemName(String itemCode) {
        return switch (itemCode) {
            case "APPLE-80" -> "苹果80果";
            case "APPLE-70" -> "苹果70果";
            case "BANANA-FEN" -> "香蕉粉蕉";
            default -> itemCode;
        };
    }

    private record Filter(String field, String operator, JsonNode value) {
    }

    private List<Filter> parseFilters(String rawPath, String param) {
        String query = rawPath.contains("?") ? rawPath.substring(rawPath.indexOf('?') + 1) : "";
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            if (!param.equals(key)) {
                continue;
            }
            String json = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            try {
                JsonNode array = MAPPER.readTree(json);
                List<Filter> filters = new ArrayList<>();
                if (array.isArray()) {
                    for (JsonNode row : array) {
                        if (row.isArray() && row.size() >= 3) {
                            filters.add(new Filter(row.get(0).asText(), row.get(1).asText(), row.get(2)));
                        }
                    }
                }
                return filters;
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private boolean matchesAnd(ObjectNode row, List<Filter> filters) {
        for (Filter filter : filters) {
            if (!matches(row, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesOr(ObjectNode row, List<Filter> filters) {
        if (filters.isEmpty()) {
            return true;
        }
        for (Filter filter : filters) {
            if (matches(row, filter)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(ObjectNode row, Filter filter) {
        JsonNode actual = row.get(filter.field);
        String actualText = actual == null || actual.isNull() ? "" : actual.asText();
        return switch (filter.operator) {
            case "=" -> actual != null && (actual.isNumber()
                    ? actual.asInt() == filter.value.asInt()
                    : actualText.equals(filter.value.asText()));
            case "!=" -> actual == null || !actualText.equals(filter.value.asText());
            case "like" -> actualText.contains(filter.value.asText().replace("%", ""));
            case ">=" -> actualText.compareTo(filter.value.asText()) >= 0;
            case "<=" -> actualText.compareTo(filter.value.asText()) <= 0;
            case "in" -> {
                if (filter.value.isArray()) {
                    for (JsonNode candidate : filter.value) {
                        if (actualText.equals(candidate.asText())) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            default -> true;
        };
    }

    private int intParam(String rawPath, String name, int fallback) {
        String value = stringParam(rawPath, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String stringParam(String rawPath, String name) {
        String query = rawPath.contains("?") ? rawPath.substring(rawPath.indexOf('?') + 1) : "";
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private List<ObjectNode> sort(List<ObjectNode> rows, String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return rows;
        }
        List<ObjectNode> sorted = new ArrayList<>(rows);
        List<String> parts = List.of(orderBy.split(","));
        sorted.sort((left, right) -> {
            for (String part : parts) {
                String token = part.trim();
                boolean desc = token.toLowerCase().endsWith(" desc");
                String field = token.replaceAll("(?i)\\s+(desc|asc)$", "").trim();
                int compared = left.path(field).asText("").compareTo(right.path(field).asText(""));
                if (compared != 0) {
                    return desc ? -compared : compared;
                }
            }
            return 0;
        });
        return sorted;
    }

    private JsonNode body(RecordedRequest request) {
        String raw = request.getBody().readUtf8();
        if (raw == null || raw.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception ex) {
            return MAPPER.createObjectNode();
        }
    }

    private static MockResponse data(ObjectNode node) {
        ObjectNode body = MAPPER.createObjectNode();
        body.set("data", node);
        return json(200, body.toString());
    }

    private static MockResponse message(ObjectNode node) {
        ObjectNode body = MAPPER.createObjectNode();
        body.set("message", node);
        return json(200, body.toString());
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }

    private static String path(RecordedRequest request) {
        String target = request.getPath() == null ? "" : request.getPath();
        int query = target.indexOf('?');
        String path = query < 0 ? target : target.substring(0, query);
        return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) ? node.path(field).asText() : fallback;
    }

    private static BigDecimal decimal(JsonNode node, BigDecimal fallback) {
        return node == null || node.isMissingNode() || node.isNull() ? fallback : node.decimalValue();
    }

    private static final String ErpSalesOrder = "Sales Order";
    private static final String ErpSalesOrderItem = "Sales Order Item";
    private static final String ErpPayment = "Payment Entry";
}
