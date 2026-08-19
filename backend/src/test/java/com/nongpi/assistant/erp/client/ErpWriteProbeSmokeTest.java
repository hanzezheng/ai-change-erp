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
    private String paymentAccount;

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
        ConfiguredMode mode = firstConfiguredMode();
        assumeTrue(mode != null, "当前 Company 没有配置 default_account 的 Mode of Payment，跳过 Write Probe");
        paymentMethod = mode.name();
        paymentAccount = mode.account();
        System.out.println("WRITE_PROBE company=" + company
                + " paymentMethod=" + paymentMethod
                + " paymentAccount=" + paymentAccount);
    }

    @Test
    @DisplayName("两商品 Draft → 同单改删增 → Submit → 收款累计 → 并发冲突")
    void probesSalesOrderAndPaymentWritePath() {
        LocalDate today = com.nongpi.assistant.erp.mapper.ErpDates.today(java.time.Clock.systemUTC());
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
        String originalPaidTo = paymentDraftMessage.path("paid_to").asText(null);
        paymentBody.put("mode_of_payment", paymentMethod);
        paymentBody.put("paid_to", paymentAccount);
        paymentBody.remove("__islocal");
        paymentBody.remove("__unsaved");
        JsonNode paymentDraft = client.createDoc(connection, "Payment Entry", paymentBody);
        String paymentId = paymentDraft.path("name").asText();
        assertThat(paymentDraft.path("docstatus").asInt()).isEqualTo(0);
        assertThat(paymentDraft.path("mode_of_payment").asText()).isEqualTo(paymentMethod);
        assertThat(paymentDraft.path("paid_to").asText()).isEqualTo(paymentAccount);
        assertThat(paymentDraft.path("difference_amount").decimalValue()).isEqualByComparingTo("0");
        assertThat(allocatedToOrder(paymentDraft, orderId)).isEqualByComparingTo("1000");
        System.out.println("WRITE_PROBE modeAccount originalPaidTo=" + originalPaidTo
                + " appliedPaidTo=" + paymentDraft.path("paid_to").asText()
                + " paid_amount=" + paymentDraft.path("paid_amount")
                + " received_amount=" + paymentDraft.path("received_amount")
                + " allocated=" + allocatedToOrder(paymentDraft, orderId));
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
        secondBody.put("mode_of_payment", paymentMethod);
        secondBody.put("paid_to", paymentAccount);
        secondBody.remove("__islocal");
        secondBody.remove("__unsaved");
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

    @Test
    @DisplayName("两张 Draft 各收满额，第二张 Submit 必须失败")
    void probesStaleDraftPaymentsCannotBothOverpay() {
        LocalDate today = com.nongpi.assistant.erp.mapper.ErpDates.today(java.time.Clock.systemUTC());
        JsonNode draft = client.createDoc(connection, "Sales Order", salesOrderPayload(today, List.of(
                item("APPLE-80", new BigDecimal("20"), "箱", new BigDecimal("68")),
                item("BANANA-FEN", new BigDecimal("30"), "件", new BigDecimal("32"))
        ), null));
        String orderId = draft.path("name").asText();
        JsonNode current = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        if (!current.hasNonNull("doctype") || current.path("doctype").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) current).put("doctype", "Sales Order");
        }
        JsonNode submitted = client.submitDoc(connection, current);
        BigDecimal total = submitted.path("grand_total").decimalValue();

        JsonNode first = createReceiveDraft(orderId, total);
        JsonNode second = createReceiveDraft(orderId, total);
        if (!first.hasNonNull("doctype") || first.path("doctype").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) first).put("doctype", "Payment Entry");
        }
        if (!second.hasNonNull("doctype") || second.path("doctype").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) second).put("doctype", "Payment Entry");
        }
        JsonNode confirmedFirst = client.submitDoc(connection, first);
        assertThat(confirmedFirst.path("docstatus").asInt()).isEqualTo(1);

        String secondError = null;
        try {
            client.submitDoc(connection, second);
        } catch (RuntimeException ex) {
            secondError = ex.getMessage();
        }
        JsonNode so = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        System.out.println("WRITE_PROBE staleDraft first=" + first.path("name").asText()
                + " second=" + second.path("name").asText()
                + " secondError=" + secondError
                + " advance_paid=" + so.path("advance_paid")
                + " grand_total=" + so.path("grand_total"));
        assertThat(secondError).as("第二张满额 Draft Submit 必须失败").isNotNull();
        assertThat(so.path("advance_paid").decimalValue()).isLessThanOrEqualTo(so.path("grand_total").decimalValue());
        assertThat(so.path("advance_paid").decimalValue()).isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("通过 Payment Entry Reference 能找到当前订单收款")
    void probesListPaymentsViaReference() {
        LocalDate today = com.nongpi.assistant.erp.mapper.ErpDates.today(java.time.Clock.systemUTC());
        JsonNode draft = client.createDoc(connection, "Sales Order", salesOrderPayload(today, List.of(
                item("APPLE-80", new BigDecimal("5"), "箱", new BigDecimal("68"))
        ), null));
        String orderId = draft.path("name").asText();
        JsonNode current = client.getDocNode(connection, "Sales Order", orderId).orElseThrow();
        if (!current.hasNonNull("doctype") || current.path("doctype").asText().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) current).put("doctype", "Sales Order");
        }
        client.submitDoc(connection, current);
        JsonNode payment = createReceiveDraft(orderId, new BigDecimal("100"));
        String paymentId = payment.path("name").asText();

        List<JsonNode> refs = client.list(connection, "Payment Entry Reference",
                ErpQuery.create()
                        .fields("name", "parent", "reference_doctype", "reference_name", "allocated_amount")
                        .filter(ErpFilter.eq("parenttype", "Payment Entry"))
                        .filter(ErpFilter.eq("reference_doctype", "Sales Order"))
                        .filter(ErpFilter.eq("reference_name", orderId))
                        .parent("Payment Entry")
                        .unlimited(),
                JsonNode.class);
        System.out.println("WRITE_PROBE paymentRefs orderId=" + orderId + " paymentId=" + paymentId + " refs=" + refs);
        assertThat(refs).isNotEmpty();
        assertThat(refs.stream().map(row -> row.path("parent").asText()).toList()).contains(paymentId);
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

    private ConfiguredMode firstConfiguredMode() {
        JsonNode methods = listRaw("Mode of Payment",
                ErpQuery.create().fields("name", "enabled", "type").filter(ErpFilter.eq("enabled", 1)).limit(0, 50));
        JsonNode accounts = listRaw("Mode of Payment Account",
                ErpQuery.create()
                        .fields("parent", "company", "default_account")
                        .filter(ErpFilter.eq("parenttype", "Mode of Payment"))
                        .filter(ErpFilter.eq("company", company))
                        .parent("Mode of Payment")
                        .unlimited());
        System.out.println("WRITE_PROBE modes=" + methods);
        System.out.println("WRITE_PROBE modeAccounts=" + accounts);
        if (!methods.isArray() || !accounts.isArray()) {
            return null;
        }
        Map<String, String> accountByMethod = new LinkedHashMap<>();
        accounts.forEach(row -> {
            String parent = row.path("parent").asText(null);
            String account = row.path("default_account").asText(null);
            if (parent != null && !parent.isBlank() && account != null && !account.isBlank()) {
                accountByMethod.putIfAbsent(parent, account);
            }
        });
        for (JsonNode method : methods) {
            String name = method.path("name").asText(null);
            String account = name == null ? null : accountByMethod.get(name);
            if (name != null && account != null) {
                return new ConfiguredMode(name, account);
            }
        }
        return null;
    }

    private JsonNode createReceiveDraft(String orderId, BigDecimal partyAmount) {
        JsonNode generated = client.callMethod(connection,
                "erpnext.accounts.doctype.payment_entry.payment_entry.get_payment_entry",
                Map.of("dt", "Sales Order", "dn", orderId, "party_amount", partyAmount));
        Map<String, Object> body = clientToMap(generated);
        body.put("doctype", "Payment Entry");
        body.put("mode_of_payment", paymentMethod);
        body.put("paid_to", paymentAccount);
        body.remove("__islocal");
        body.remove("__unsaved");
        return client.createDoc(connection, "Payment Entry", body);
    }

    private BigDecimal allocatedToOrder(JsonNode payment, String orderId) {
        for (JsonNode ref : payment.path("references")) {
            if ("Sales Order".equals(ref.path("reference_doctype").asText())
                    && orderId.equals(ref.path("reference_name").asText())) {
                return ref.path("allocated_amount").decimalValue();
            }
        }
        throw new AssertionError("Payment Entry 没有关联 Sales Order " + orderId);
    }

    private record ConfiguredMode(String name, String account) {
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
