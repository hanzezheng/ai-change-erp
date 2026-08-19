package com.nongpi.assistant.erp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.erp.connection.ErpConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 真实 ERPNext v16 写行为探测。默认 {@code ./mvnw test} / CI 不运行。
 *
 * <p>启用：设置 {@code ERP_RUN_WRITE_PROBE=true}，并提供
 * {@code ERP_BASE_URL}/{@code ERP_URL}、{@code ERP_API_KEY}/{@code ERP_KEY}、
 * {@code ERP_API_SECRET}/{@code ERP_SECRET}。然后：
 * {@code ./mvnw test -Dsurefire.excludedGroups= -Dtest=com.nongpi.assistant.erp.client.ErpWriteProbeSmokeTest}
 */
@Tag("erp-smoke")
@EnabledIfEnvironmentVariable(named = "ERP_RUN_WRITE_PROBE", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("真实 ERPNext Write Probe")
class ErpWriteProbeSmokeTest {

    private ErpRestClient client;
    private ErpConnection connection;
    private String company;
    private String paymentMethod;

    @BeforeAll
    void connect() {
        String baseUrl = firstEnv("ERP_BASE_URL", "ERP_URL");
        String apiKey = firstEnv("ERP_API_KEY", "ERP_KEY");
        String apiSecret = firstEnv("ERP_API_SECRET", "ERP_SECRET");
        assumeTrue(baseUrl != null && apiKey != null && apiSecret != null,
                "未配置真实 ERPNext 凭据，跳过 Write Probe");

        client = new ErpRestClient(new ObjectMapper());
        String configuredCompany = firstEnv("ERP_DEFAULT_COMPANY");
        connection = new ErpConnection(
                "probe",
                trimSlash(baseUrl),
                apiKey,
                apiSecret,
                firstEnv("ERP_SELLING_PRICE_LIST") == null ? "Standard Selling" : firstEnv("ERP_SELLING_PRICE_LIST"),
                firstEnv("ERP_DEFAULT_WAREHOUSE"),
                configuredCompany,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );
        company = configuredCompany != null ? configuredCompany : firstCompany();
        paymentMethod = firstUsablePaymentMethod();
        System.out.println("WRITE_PROBE company=" + company + " paymentMethod=" + paymentMethod);
    }

    @Test
    @DisplayName("两商品 Draft → 同单改删增 → Submit → 收款累计 → 并发冲突")
    void probesSalesOrderAndPaymentWritePath() {
        LocalDate today = LocalDate.now();
        Map<String, Object> create = salesOrderPayload(today, List.of(
                item("APPLE-80", new BigDecimal("20"), "箱", new BigDecimal("68")),
                item("BANANA-FEN", new BigDecimal("30"), "件", new BigDecimal("32"))
        ), "probe-note-remarks");

        JsonNode draft = client.createDoc(connection, "Sales Order", create);
        String orderId = draft.path("name").asText();
        assertThat(orderId).isNotBlank();
        assertThat(draft.path("docstatus").asInt()).isEqualTo(0);
        assertThat(draft.path("items")).hasSize(2);
        String firstItemName = draft.path("items").get(0).path("name").asText();
        String bananaItemName = draft.path("items").get(1).path("name").asText();
        assertThat(firstItemName).isNotBlank();
        System.out.println("WRITE_PROBE created orderId=" + orderId
                + " grand_total=" + draft.path("grand_total")
                + " apple_rate=" + draft.path("items").get(0).path("rate")
                + " remarks=" + draft.path("remarks").asText(null)
                + " conversion_factor=" + draft.path("items").get(0).path("conversion_factor"));

        String oldModified = draft.path("modified").asText();

        List<Map<String, Object>> updatedItems = new ArrayList<>();
        updatedItems.add(namedItem(firstItemName, "APPLE-80", new BigDecimal("30"), "箱", new BigDecimal("70")));
        updatedItems.add(item("APPLE-70", new BigDecimal("5"), "箱", new BigDecimal("50")));
        Map<String, Object> update = salesOrderPayload(today, updatedItems, "probe-note-updated");
        update.put("name", orderId);
        update.put("modified", oldModified);

        JsonNode updated = client.updateDoc(connection, "Sales Order", orderId, update);
        assertThat(updated.path("name").asText()).isEqualTo(orderId);
        assertThat(updated.path("docstatus").asInt()).isEqualTo(0);
        assertThat(updated.path("items")).hasSize(2);
        List<String> itemCodes = new ArrayList<>();
        updated.path("items").forEach(row -> itemCodes.add(row.path("item_code").asText()));
        assertThat(itemCodes).containsExactlyInAnyOrder("APPLE-80", "APPLE-70");
        assertThat(itemCodes).doesNotContain("BANANA-FEN");
        BigDecimal appleQty = decimal(updated.path("items"), "APPLE-80", "qty");
        BigDecimal appleRate = decimal(updated.path("items"), "APPLE-80", "rate");
        assertThat(appleQty).isEqualByComparingTo("30");
        System.out.println("WRITE_PROBE updated same order qty=" + appleQty + " rate=" + appleRate
                + " bananaGone=" + !itemCodes.contains("BANANA-FEN")
                + " bananaWas=" + bananaItemName);

        Map<String, Object> stale = salesOrderPayload(today, updatedItems, "stale");
        stale.put("name", orderId);
        stale.put("modified", oldModified);
        assertThatThrownBy(() -> client.updateDoc(connection, "Sales Order", orderId, stale))
                .hasMessageContaining("订单已被其他人修改");

        JsonNode current = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        if (!current.has("doctype") || current.path("doctype").isNull() || current.path("doctype").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) current).put("doctype", "Sales Order");
        }
        JsonNode submitted = client.submitDoc(connection, current);
        assertThat(submitted.path("name").asText()).isEqualTo(orderId);
        JsonNode afterSubmit = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        assertThat(afterSubmit.path("docstatus").asInt()).isEqualTo(1);
        String submittedStatus = afterSubmit.path("status").asText();
        System.out.println("WRITE_PROBE submitted docstatus=" + afterSubmit.path("docstatus").asInt()
                + " status=" + submittedStatus
                + " grand_total=" + afterSubmit.path("grand_total")
                + " advance_paid=" + afterSubmit.path("advance_paid"));
        assertThat(submittedStatus).isNotEqualTo("Completed");

        Map<String, Object> illegalUpdate = salesOrderPayload(today, updatedItems, "illegal");
        illegalUpdate.put("name", orderId);
        assertThatThrownBy(() -> client.updateDoc(connection, "Sales Order", orderId, illegalUpdate));

        BigDecimal orderTotal = afterSubmit.path("grand_total").decimalValue();
        JsonNode paymentDraftMessage = client.callMethod(connection,
                "erpnext.accounts.doctype.payment_entry.payment_entry.get_payment_entry",
                Map.of("dt", "Sales Order", "dn", orderId, "party_amount", 1000));
        System.out.println("WRITE_PROBE get_payment_entry keys=" + fieldNames(paymentDraftMessage)
                + " paid_from=" + paymentDraftMessage.path("paid_from").asText(null)
                + " paid_to=" + paymentDraftMessage.path("paid_to").asText(null)
                + " party=" + paymentDraftMessage.path("party").asText(null)
                + " references=" + paymentDraftMessage.path("references"));

        Map<String, Object> paymentBody = clientToMap(paymentDraftMessage);
        paymentBody.put("doctype", "Payment Entry");
        if (paymentMethod != null) {
            paymentBody.put("mode_of_payment", paymentMethod);
        }
        paymentBody.put("paid_amount", 1000);
        paymentBody.put("received_amount", 1000);
        JsonNode paymentDraft = client.createDoc(connection, "Payment Entry", paymentBody);
        String paymentId = paymentDraft.path("name").asText();
        assertThat(paymentDraft.path("docstatus").asInt()).isEqualTo(0);
        JsonNode soWhileDraft = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        System.out.println("WRITE_PROBE paymentDraft=" + paymentId
                + " so.advance_paid while draft=" + soWhileDraft.path("advance_paid"));

        if (!paymentDraft.has("doctype") || paymentDraft.path("doctype").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) paymentDraft).put("doctype", "Payment Entry");
        }
        JsonNode confirmed = client.submitDoc(connection, paymentDraft);
        assertThat(confirmed.path("name").asText()).isEqualTo(paymentId);
        JsonNode confirmedPe = client.getDocNode(connection, "Payment Entry", paymentId).orElseThrow();
        JsonNode soAfterFirstPay = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        System.out.println("WRITE_PROBE paymentConfirmed docstatus=" + confirmedPe.path("docstatus").asInt()
                + " difference_amount=" + confirmedPe.path("difference_amount")
                + " party=" + confirmedPe.path("party").asText()
                + " advance_paid=" + soAfterFirstPay.path("advance_paid")
                + " advance_payment_status=" + soAfterFirstPay.path("advance_payment_status").asText(null)
                + " so.status=" + soAfterFirstPay.path("status").asText());
        assertThat(confirmedPe.path("docstatus").asInt()).isEqualTo(1);
        assertThat(confirmedPe.path("party").asText()).isEqualTo("韩兆亮");
        assertThat(soAfterFirstPay.path("advance_paid").decimalValue())
                .isEqualByComparingTo("1000");
        assertThat(soAfterFirstPay.path("status").asText()).isNotEqualTo("Completed");

        BigDecimal remaining = orderTotal.subtract(new BigDecimal("1000"));
        JsonNode secondMessage = client.callMethod(connection,
                "erpnext.accounts.doctype.payment_entry.payment_entry.get_payment_entry",
                Map.of("dt", "Sales Order", "dn", orderId, "party_amount", remaining));
        Map<String, Object> secondBody = clientToMap(secondMessage);
        secondBody.put("doctype", "Payment Entry");
        if (paymentMethod != null) {
            secondBody.put("mode_of_payment", paymentMethod);
        }
        secondBody.put("paid_amount", remaining);
        secondBody.put("received_amount", remaining);
        JsonNode secondDraft = client.createDoc(connection, "Payment Entry", secondBody);
        if (!secondDraft.has("doctype") || secondDraft.path("doctype").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) secondDraft).put("doctype", "Payment Entry");
        }
        client.submitDoc(connection, secondDraft);
        JsonNode soPaid = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        System.out.println("WRITE_PROBE afterSecondPay advance_paid=" + soPaid.path("advance_paid")
                + " grand_total=" + soPaid.path("grand_total")
                + " status=" + soPaid.path("status").asText()
                + " advance_payment_status=" + soPaid.path("advance_payment_status").asText(null));
        assertThat(soPaid.path("advance_paid").decimalValue()).isEqualByComparingTo(orderTotal);
        assertThat(soPaid.path("status").asText()).isNotEqualTo("Completed");

        JsonNode jinDraft = client.createDoc(connection, "Sales Order", salesOrderPayload(today, List.of(
                item("APPLE-80", new BigDecimal("10"), "斤", new BigDecimal("3.8"))
        ), null));
        System.out.println("WRITE_PROBE jin uom rate=" + jinDraft.path("items").get(0).path("rate")
                + " uom=" + jinDraft.path("items").get(0).path("uom").asText()
                + " conversion_factor=" + jinDraft.path("items").get(0).path("conversion_factor"));
        assertThat(jinDraft.path("items").get(0).path("uom").asText()).isEqualTo("斤");
        assertThat(jinDraft.path("items").get(0).path("rate").decimalValue())
                .isNotEqualByComparingTo("68");
    }

    private Map<String, Object> salesOrderPayload(LocalDate date, List<Map<String, Object>> items, String remarks) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("doctype", "Sales Order");
        payload.put("customer", "韩兆亮");
        payload.put("company", company);
        payload.put("transaction_date", date.toString());
        payload.put("delivery_date", date.toString());
        payload.put("selling_price_list", connection.sellingPriceList());
        payload.put("items", items);
        if (remarks != null) {
            payload.put("remarks", remarks);
        }
        return payload;
    }

    private static Map<String, Object> item(String itemCode, BigDecimal qty, String uom, BigDecimal rate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("item_code", itemCode);
        row.put("qty", qty);
        row.put("uom", uom);
        row.put("rate", rate);
        return row;
    }

    private static Map<String, Object> namedItem(String name, String itemCode, BigDecimal qty, String uom, BigDecimal rate) {
        Map<String, Object> row = item(itemCode, qty, uom, rate);
        row.put("name", name);
        return row;
    }

    private String firstCompany() {
        JsonNode companies = listRaw("Company", ErpQuery.create().fields("name").limit(0, 5));
        assertThat(companies.isArray() && companies.size() > 0).as("真实站点必须有 Company").isTrue();
        return companies.get(0).path("name").asText();
    }

    private String firstUsablePaymentMethod() {
        JsonNode methods = listRaw("Mode of Payment", ErpQuery.create().fields("name", "enabled", "type").limit(0, 20));
        System.out.println("WRITE_PROBE modes=" + methods);
        if (!methods.isArray() || methods.isEmpty()) {
            return null;
        }
        return methods.get(0).path("name").asText(null);
    }

    private JsonNode listRaw(String doctype, ErpQuery query) {
        List<JsonNode> rows = client.list(connection, doctype, query, JsonNode.class);
        com.fasterxml.jackson.databind.node.ArrayNode array = new ObjectMapper().createArrayNode();
        rows.forEach(array::add);
        return array;
    }

    private BigDecimal decimal(JsonNode items, String itemCode, String field) {
        for (JsonNode row : items) {
            if (itemCode.equals(row.path("item_code").asText())) {
                return row.path(field).decimalValue();
            }
        }
        throw new AssertionError("没有商品 " + itemCode);
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> clientToMap(JsonNode node) {
        return new ObjectMapper().convertValue(node, Map.class);
    }

    private static String firstEnv(String... keys) {
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
