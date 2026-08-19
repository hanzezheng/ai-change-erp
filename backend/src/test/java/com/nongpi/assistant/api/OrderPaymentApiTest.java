package com.nongpi.assistant.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nongpi.assistant.erp.support.FakeErpCatalog;
import com.nongpi.assistant.erp.support.FakeErpNext;
import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantEntity;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.saas.user.AppUserEntity;
import com.nongpi.assistant.saas.user.UserStatus;
import com.nongpi.assistant.support.AbstractSaasIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("订单与收款写链路")
class OrderPaymentApiTest extends AbstractSaasIntegrationTest {

    private static final FakeErpNext ERP = start();

    private String token;

    private static FakeErpNext start() {
        try {
            FakeErpNext erp = new FakeErpNext();
            erp.start();
            return erp;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @BeforeEach
    void seed() {
        ERP.reset();
        FakeErpCatalog.goldenPath(ERP);
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        newErpConnection(tenant, ERP.baseUrl(), "key", "secret");
        token = accessToken(user, membership);
    }

    @AfterAll
    static void shutdown() throws IOException {
        ERP.close();
    }

    @Test
    @DisplayName("创建两商品 Draft，正式 ID 使用 Sales Order.name")
    void createTwoItemDraft() throws Exception {
        JsonNode body = createDraft(twoItemJson(), "key-1");
        assertThat(body.path("orderId").asText()).startsWith("SAL-ORD-");
        assertThat(body.path("erpSalesOrderId").isMissingNode()).isTrue();
        assertThat(body.path("orderStatus").asText()).isEqualTo("DRAFT");
        assertThat(body.path("items")).hasSize(2);
        assertThat(body.path("items").get(0).path("orderItemId").asText()).isNotBlank();
        assertThat(body.path("items").get(0).path("itemCode").asText()).isEqualTo("APPLE-80");
        assertThat(body.path("items").get(0).path("spec").asText()).isEqualTo("80果");
        assertThat(body.path("totalAmount").decimalValue()).isEqualByComparingTo("2320");
        assertThat(body.path("paymentStatus").asText()).isEqualTo("UNPAID");
    }

    @Test
    @DisplayName("客户不存在时整单失败")
    void customerNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "k-cust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("不存在的客户", apple(20, "箱", 68))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("商品不存在时整单失败，不静默删除")
    void itemNotFoundDoesNotDropLine() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "k-item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("韩兆亮",
                                line("NO-SUCH", 1, "箱", 1),
                                apple(20, "箱", 68))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/orders").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("非法 UOM 拒绝建单")
    void invalidUom() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "k-uom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("韩兆亮", apple(20, "袋", 68))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UOM"));
    }

    @Test
    @DisplayName("qty <= 0 拒绝建单")
    void invalidQty() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "k-qty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("韩兆亮", apple(0, "箱", 68))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    @DisplayName("rate < 0 拒绝建单")
    void invalidRate() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "k-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("韩兆亮", apple(20, "箱", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RATE"));
    }

    @Test
    @DisplayName("更新同一张 Draft：改数量、加行、删行")
    void updateSameDraft() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-upd");
        String orderId = created.path("orderId").asText();
        String appleRow = created.path("items").get(0).path("orderItemId").asText();
        JsonNode updated = read(mockMvc.perform(put("/api/v1/orders/" + orderId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "items": [
                                    {"orderItemId": "%s", "itemCode": "APPLE-80", "qty": 30, "uom": "箱", "rate": 68},
                                    {"itemCode": "APPLE-70", "qty": 5, "uom": "箱", "rate": 50}
                                  ]
                                }
                                """.formatted(created.path("updatedAt").asText(), appleRow)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updated.path("orderId").asText()).isEqualTo(orderId);
        assertThat(updated.path("items")).hasSize(2);
        assertThat(itemCodes(updated)).containsExactlyInAnyOrder("APPLE-80", "APPLE-70");
        assertThat(qtyOf(updated, "APPLE-80")).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("切换 UOM 时不能沿用箱价")
    void updateUomUsesNewRate() throws Exception {
        JsonNode created = createDraft(orderJson("韩兆亮", apple(20, "箱", 68)), "k-uom-upd");
        String row = created.path("items").get(0).path("orderItemId").asText();
        JsonNode updated = read(mockMvc.perform(put("/api/v1/orders/" + created.path("orderId").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "items": [{"orderItemId": "%s", "itemCode": "APPLE-80", "qty": 10, "uom": "斤", "rate": 3.8}]
                                }
                                """.formatted(created.path("updatedAt").asText(), row)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updated.path("items").get(0).path("uom").asText()).isEqualTo("斤");
        assertThat(updated.path("items").get(0).path("rate").decimalValue()).isEqualByComparingTo("3.8");
        assertThat(updated.path("totalAmount").decimalValue()).isNotEqualByComparingTo("1360");
    }

    @Test
    @DisplayName("过期 expectedModifiedAt 返回 ORDER_CONFLICT")
    void optimisticConflict() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-conflict");
        String orderId = created.path("orderId").asText();
        String stale = created.path("updatedAt").asText();
        mockMvc.perform(put("/api/v1/orders/" + orderId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "items": [{"itemCode": "APPLE-80", "qty": 21, "uom": "箱", "rate": 68}]
                                }
                                """.formatted(stale)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/orders/" + orderId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "items": [{"itemCode": "APPLE-80", "qty": 22, "uom": "箱", "rate": 68}]
                                }
                                """.formatted(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));
    }

    @Test
    @DisplayName("提交 Draft，重复提交自然幂等")
    void submitIsIdempotent() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-sub");
        String orderId = created.path("orderId").asText();
        JsonNode submitted = read(mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("SUBMITTED"))
                .andReturn());
        JsonNode again = read(mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(again.path("orderId").asText()).isEqualTo(submitted.path("orderId").asText());
    }

    @Test
    @DisplayName("已提交订单拒绝普通 PUT")
    void putSubmittedRejected() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-put-sub");
        String orderId = created.path("orderId").asText();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/orders/" + orderId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "items": [{"itemCode": "APPLE-80", "qty": 20, "uom": "箱", "rate": 68}]
                                }
                                """.formatted(created.path("updatedAt").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_INVALID"));
    }

    @Test
    @DisplayName("取消与完成状态映射")
    void mapsCancelledAndCompleted() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-map");
        String cancelledId = created.path("orderId").asText();
        ERP.setDocstatus("Sales Order", cancelledId, 2, "Cancelled");
        mockMvc.perform(get("/api/v1/orders/" + cancelledId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));

        JsonNode other = createDraft(twoItemJson(), "k-map-2");
        ERP.setDocstatus("Sales Order", other.path("orderId").asText(), 1, "Completed");
        mockMvc.perform(get("/api/v1/orders/" + other.path("orderId").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.orderStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("订单列表与详情")
    void listAndDetail() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-list");
        mockMvc.perform(get("/api/v1/orders").param("q", "韩兆亮").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(created.path("orderId").asText()))
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.content[0].erpSalesOrderId").doesNotExist());
        mockMvc.perform(get("/api/v1/orders/" + created.path("orderId").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("付款方式来自 ERPNext Mode of Payment")
    void paymentMethodsFromErp() throws Exception {
        mockMvc.perform(get("/api/v1/payment-methods").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentMethodId").value("微信"))
                .andExpect(jsonPath("$[?(@.paymentMethodId == 'WECHAT_TRANSFER')]").isEmpty());
    }

    @Test
    @DisplayName("payment-methods 不返回当前公司未配置账户的方式")
    void paymentMethodsOmitUnconfiguredMode() throws Exception {
        mockMvc.perform(get("/api/v1/payment-methods").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentMethodId").value("微信"))
                .andExpect(jsonPath("$[0].paymentMethodName").value("微信"))
                .andExpect(jsonPath("$[?(@.paymentMethodId == '未配置')]").isEmpty())
                .andExpect(jsonPath("$[0].defaultAccount").doesNotExist());
    }

    @Test
    @DisplayName("Draft 收款不计入 confirmedPaid；确认后累计两笔且不完成订单")
    void cumulativePaymentsDoNotCompleteOrder() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-pay");
        String orderId = created.path("orderId").asText();
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        JsonNode draftPay = read(mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1000, "微信")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.erpPaymentEntryId").doesNotExist())
                .andReturn());
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.confirmedPaid").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));

        mockMvc.perform(post("/api/v1/payments/" + draftPay.path("paymentId").asText() + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"));
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/payment-summary")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.confirmedPaid").value(1000))
                .andExpect(jsonPath("$.remainingToCollect").value(1320))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.outstandingAmount").doesNotExist());

        JsonNode second = read(mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "pay-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1320, "微信")))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/v1/payments/" + second.path("paymentId").asText() + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.remainingToCollect").value(0))
                .andExpect(jsonPath("$.orderStatus").value("SUBMITTED"));
    }

    @Test
    @DisplayName("重复确认同一收款不新建")
    void confirmTwiceNoDuplicate() throws Exception {
        String orderId = submitTwoItem("k-dup-pay");
        JsonNode pay = read(mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "pay-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1000, "微信")))
                .andReturn());
        String paymentId = pay.path("paymentId").asText();
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/confirm").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.paymentId").value(paymentId));
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/confirm").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.paymentId").value(paymentId));
        mockMvc.perform(get("/api/v1/payments").param("relatedOrderId", orderId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("客户不一致或超额或非法付款方式被拒绝")
    void paymentValidation() throws Exception {
        String orderId = submitTwoItem("k-pay-val");
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "bad-cust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJsonWithCustomer("别人", orderId, 1000, "微信")))
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "too-much")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 99999, "微信")))
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "bad-m")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1000, "WECHAT_TRANSFER")))
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "no-acct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1000, "未配置")))
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
    }

    @Test
    @DisplayName("相同 Idempotency-Key 返回同一订单；不同 body 冲突")
    void createOrderIdempotency() throws Exception {
        JsonNode first = createDraft(twoItemJson(), "same-key");
        JsonNode second = createDraft(twoItemJson(), "same-key");
        assertThat(second.path("orderId").asText()).isEqualTo(first.path("orderId").asText());
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("韩兆亮", apple(1, "箱", 68))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("相同 Idempotency-Key 返回同一收款")
    void createPaymentIdempotency() throws Exception {
        String orderId = submitTwoItem("k-pay-idemp");
        JsonNode first = read(mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "pay-same")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1000, "微信")))
                .andReturn());
        JsonNode second = read(mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "pay-same")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1000, "微信")))
                .andReturn());
        assertThat(second.path("paymentId").asText()).isEqualTo(first.path("paymentId").asText());
    }

    @Test
    @DisplayName("写超时标记 UNKNOWN，不会自动再创建")
    void unknownOutcomeDoesNotDuplicate() throws Exception {
        ERP.hangNextWrite();
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "unknown-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoItemJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_OUTCOME_UNKNOWN"));
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "unknown-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoItemJson()))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_OUTCOME_UNKNOWN"));
        mockMvc.perform(get("/api/v1/orders").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("lastDealPrice 只用已提交且未取消订单，并按 customer+item+UOM")
    void lastDealPriceHistory() throws Exception {
        JsonNode draft = createDraft(twoItemJson(), "k-hist-draft");
        mockMvc.perform(get("/api/v1/pricing/last-deal")
                        .param("customerId", "韩兆亮")
                        .param("itemCode", "APPLE-80")
                        .param("uom", "箱")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").doesNotExist());

        mockMvc.perform(post("/api/v1/orders/" + draft.path("orderId").asText() + "/submit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pricing/last-deal")
                        .param("customerId", "韩兆亮")
                        .param("itemCode", "APPLE-80")
                        .param("uom", "箱")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.price").value(68))
                .andExpect(jsonPath("$.uom").value("箱"))
                .andExpect(jsonPath("$.sourceOrderId").value(draft.path("orderId").asText()));

        mockMvc.perform(get("/api/v1/pricing/last-deal")
                        .param("customerId", "韩兆亮")
                        .param("itemCode", "APPLE-80")
                        .param("uom", "斤")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.price").doesNotExist());

        JsonNode cancelled = createDraft(twoItemJson(), "k-hist-cancel");
        mockMvc.perform(post("/api/v1/orders/" + cancelled.path("orderId").asText() + "/submit")
                        .header("Authorization", bearer(token)));
        ERP.setDocstatus("Sales Order", cancelled.path("orderId").asText(), 2, "Cancelled");
        JsonNode latest = createDraft(orderJson("韩兆亮", apple(8, "箱", 65)), "k-hist-new");
        mockMvc.perform(post("/api/v1/orders/" + latest.path("orderId").asText() + "/submit")
                        .header("Authorization", bearer(token)));
        mockMvc.perform(get("/api/v1/pricing/last-deal")
                        .param("customerId", "韩兆亮")
                        .param("itemCode", "APPLE-80")
                        .param("uom", "箱")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.price").value(65));
    }

    @Test
    @DisplayName("已提交订单进入客户 recent 与商品 frequentItems")
    void selectorUsesSubmittedHistory() throws Exception {
        String orderId = submitTwoItem("k-selector");
        mockMvc.perform(get("/api/v1/customers/selector").param("q", "韩")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent[0].customerId").value("韩兆亮"));
        mockMvc.perform(get("/api/v1/products/selector")
                        .param("q", "苹果")
                        .param("customerId", "韩兆亮")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequentItems.length()").value(2))
                .andExpect(jsonPath("$.frequentItems[?(@.itemCode == 'APPLE-80')].lastDealPrice").value(org.hamcrest.Matchers.hasItem(68.0)));
        assertThat(orderId).isNotBlank();
    }

    @Test
    @DisplayName("创建收款使用所选 Mode 的 default_account，不覆盖 ERP 生成金额")
    void createPaymentAppliesModeAccountWithoutOverwritingAmounts() throws Exception {
        String orderId = submitTwoItem("k-mop-acct");
        JsonNode pay = read(mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "pay-mop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, 1000, "微信")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.relatedOrderId").value(orderId))
                .andExpect(jsonPath("$.paymentMethodId").value("微信"))
                .andReturn());
        var stored = ERP.paymentDoc(pay.path("paymentId").asText());
        assertThat(stored.path("mode_of_payment").asText()).isEqualTo("微信");
        assertThat(stored.path("paid_to").asText()).isEqualTo("WeChat - NPT");
        assertThat(stored.path("paid_to").asText()).isNotEqualTo("Cash - NPT");
        assertThat(stored.path("paid_amount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(stored.path("received_amount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(stored.path("source_exchange_rate").decimalValue()).isEqualByComparingTo("1");
        assertThat(stored.path("target_exchange_rate").decimalValue()).isEqualByComparingTo("1");
        assertThat(stored.path("difference_amount").decimalValue()).isEqualByComparingTo("0");
        assertThat(stored.path("references").get(0).path("allocated_amount").decimalValue())
                .isEqualByComparingTo("1000");
        assertThat(stored.path("references").get(0).path("reference_name").asText()).isEqualTo(orderId);
        assertThat(pay.path("amount").decimalValue())
                .isEqualByComparingTo(stored.path("references").get(0).path("allocated_amount").decimalValue());
    }

    @Test
    @DisplayName("confirm 正常 Order-related Customer Receive")
    void confirmSupportedReceive() throws Exception {
        String orderId = submitTwoItem("k-confirm-ok");
        JsonNode pay = createPayment(orderId, 1000, "pay-ok");
        mockMvc.perform(post("/api/v1/payments/" + pay.path("paymentId").asText() + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.relatedOrderId").value(orderId))
                .andExpect(jsonPath("$.amount").value(1000));
    }

    @Test
    @DisplayName("confirm 无 Sales Order reference 被拒绝")
    void confirmWithoutSalesOrderRejected() throws Exception {
        JsonNode pay = createPayment(submitTwoItem("k-confirm-noso"), 1000, "pay-noso");
        ERP.mutatePayment(pay.path("paymentId").asText(), node -> node.putArray("references"));
        confirmRejected(pay.path("paymentId").asText(), "PAYMENT_NOT_SUPPORTED");
    }

    @Test
    @DisplayName("confirm Supplier Payment 被拒绝")
    void confirmSupplierPayRejected() throws Exception {
        JsonNode pay = createPayment(submitTwoItem("k-confirm-sup"), 1000, "pay-sup");
        ERP.mutatePayment(pay.path("paymentId").asText(), node -> {
            node.put("payment_type", "Pay");
            node.put("party_type", "Supplier");
            node.put("party", "某供应商");
        });
        confirmRejected(pay.path("paymentId").asText(), "PAYMENT_NOT_SUPPORTED");
    }

    @Test
    @DisplayName("confirm Internal Transfer 被拒绝")
    void confirmInternalTransferRejected() throws Exception {
        JsonNode pay = createPayment(submitTwoItem("k-confirm-it"), 1000, "pay-it");
        ERP.mutatePayment(pay.path("paymentId").asText(), node -> {
            node.put("payment_type", "Internal Transfer");
            node.put("party_type", "");
            node.putArray("references");
        });
        confirmRejected(pay.path("paymentId").asText(), "PAYMENT_NOT_SUPPORTED");
    }

    @Test
    @DisplayName("confirm 其他 Company 的 Payment Entry 被拒绝")
    void confirmOtherCompanyRejected() throws Exception {
        JsonNode pay = createPayment(submitTwoItem("k-confirm-co"), 1000, "pay-co");
        ERP.mutatePayment(pay.path("paymentId").asText(), node -> node.put("company", "其他公司"));
        confirmRejected(pay.path("paymentId").asText(), "PAYMENT_NOT_SUPPORTED");
    }

    @Test
    @DisplayName("confirm 客户与订单不一致被拒绝")
    void confirmCustomerMismatchRejected() throws Exception {
        JsonNode pay = createPayment(submitTwoItem("k-confirm-party"), 1000, "pay-party");
        ERP.mutatePayment(pay.path("paymentId").asText(), node -> node.put("party", "别人"));
        confirmRejected(pay.path("paymentId").asText(), "PAYMENT_INVALID");
    }

    @Test
    @DisplayName("两张过期 Draft 不能都超收同一订单")
    void twoStaleDraftsCannotBothOverpay() throws Exception {
        String orderId = submitTwoItem("k-stale-pay");
        JsonNode first = createPayment(orderId, 2320, "stale-a");
        JsonNode second = createPayment(orderId, 2320, "stale-b");
        mockMvc.perform(post("/api/v1/payments/" + first.path("paymentId").asText() + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/" + second.path("paymentId").asText() + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID"));
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.confirmedPaid").value(2320))
                .andExpect(jsonPath("$.remainingToCollect").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    @DisplayName("POST 带 orderItemId 被拒绝")
    void postWithOrderItemIdRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "k-post-row")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "items": [{"orderItemId": "row-from-client", "itemCode": "APPLE-80", "qty": 20, "uom": "箱", "rate": 68}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PUT 使用其他订单的 orderItemId 被拒绝")
    void putForeignOrderItemIdRejected() throws Exception {
        JsonNode first = createDraft(orderJson("韩兆亮", apple(20, "箱", 68)), "k-row-a");
        JsonNode second = createDraft(orderJson("韩兆亮", apple(10, "箱", 68)), "k-row-b");
        String foreignRow = first.path("items").get(0).path("orderItemId").asText();
        mockMvc.perform(put("/api/v1/orders/" + second.path("orderId").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "items": [{"orderItemId": "%s", "itemCode": "APPLE-80", "qty": 12, "uom": "箱", "rate": 68}]
                                }
                                """.formatted(second.path("updatedAt").asText(), foreignRow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PUT 重复 orderItemId 被拒绝")
    void putDuplicateOrderItemIdRejected() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-dup-row");
        String row = created.path("items").get(0).path("orderItemId").asText();
        mockMvc.perform(put("/api/v1/orders/" + created.path("orderId").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "items": [
                                    {"orderItemId": "%s", "itemCode": "APPLE-80", "qty": 20, "uom": "箱", "rate": 68},
                                    {"orderItemId": "%s", "itemCode": "BANANA-FEN", "qty": 30, "uom": "件", "rate": 32}
                                  ]
                                }
                                """.formatted(created.path("updatedAt").asText(), row, row)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PUT 缺少 transactionDate 返回 400")
    void putMissingTransactionDateRejected() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-put-date");
        mockMvc.perform(put("/api/v1/orders/" + created.path("orderId").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "expectedModifiedAt": "%s",
                                  "items": [{"itemCode": "APPLE-80", "qty": 20, "uom": "箱", "rate": 68}]
                                }
                                """.formatted(created.path("updatedAt").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("创建订单默认日期使用经营时区")
    void createDefaultDateUsesBusinessZone() throws Exception {
        JsonNode created = createDraft(
                "{\"customerId\":\"韩兆亮\",\"items\":[" + apple(20, "箱", 68) + "]}",
                "k-biz-date");
        assertThat(created.path("transactionDate").asText())
                .isEqualTo(com.nongpi.assistant.erp.mapper.ErpDates.today(java.time.Clock.systemUTC()).toString());
    }

    @Test
    @DisplayName("创建订单非空 note 被拒绝")
    void createOrderNoteRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "k-note-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "note": "备货备注",
                                  "items": [{"itemCode": "APPLE-80", "qty": 20, "uom": "箱", "rate": 68}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FIELD"))
                .andExpect(jsonPath("$.message").value("当前版本暂不支持订单/收款备注"));
    }

    @Test
    @DisplayName("更新订单非空 note 被拒绝")
    void updateOrderNoteRejected() throws Exception {
        JsonNode created = createDraft(twoItemJson(), "k-note-upd");
        mockMvc.perform(put("/api/v1/orders/" + created.path("orderId").asText())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "transactionDate": "2026-08-19",
                                  "expectedModifiedAt": "%s",
                                  "note": "不要丢",
                                  "items": [{"itemCode": "APPLE-80", "qty": 20, "uom": "箱", "rate": 68}]
                                }
                                """.formatted(created.path("updatedAt").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FIELD"));
    }

    @Test
    @DisplayName("创建收款非空 note 被拒绝")
    void createPaymentNoteRejected() throws Exception {
        String orderId = submitTwoItem("k-note-pay");
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "pay-note")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "韩兆亮",
                                  "relatedOrderId": "%s",
                                  "amount": 1000,
                                  "paymentMethodId": "微信",
                                  "note": "微信截图"
                                }
                                """.formatted(orderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FIELD"));
    }

    @Test
    @DisplayName("订单收款即使不在全站最新 50 条也能查到，无关收款不出现")
    void listPaymentsByReferenceNotGlobalWindow() throws Exception {
        String orderId = submitTwoItem("k-pay-list");
        JsonNode pay = createPayment(orderId, 1000, "pay-list-target");
        String otherOrderId = submitTwoItem("k-pay-list-other");
        JsonNode other = createPayment(otherOrderId, 500, "pay-list-other");
        ERP.seedUnrelatedPayments(60);
        JsonNode listed = read(mockMvc.perform(get("/api/v1/payments")
                        .param("relatedOrderId", orderId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(listed.path("content")).hasSize(1);
        assertThat(listed.path("content").get(0).path("paymentId").asText())
                .isEqualTo(pay.path("paymentId").asText());
        assertThat(listed.path("content").get(0).path("paymentId").asText())
                .isNotEqualTo(other.path("paymentId").asText());
        assertThat(listed.path("content").get(0).path("amount").decimalValue())
                .isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("缺少 defaultCompany 时拒绝写入")
    void missingCompany() throws Exception {
        TenantEntity tenant = newTenant("无公司档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("no-co", "password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        var entity = newErpConnection(tenant, ERP.baseUrl(), "k", "s");
        entity.setDefaultCompany(null);
        erpConnectionRepository.save(entity);
        String other = accessToken(user, membership);
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(other))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoItemJson()))
                .andExpect(jsonPath("$.code").value("ERP_WRITE_CONFIGURATION_INCOMPLETE"));
    }

    private String submitTwoItem(String key) throws Exception {
        JsonNode created = createDraft(twoItemJson(), key);
        mockMvc.perform(post("/api/v1/orders/" + created.path("orderId").asText() + "/submit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return created.path("orderId").asText();
    }

    private JsonNode createPayment(String orderId, int amount, String idempotencyKey) throws Exception {
        return read(mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, amount, "微信")))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void confirmRejected(String paymentId, String code) throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/confirm")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private JsonNode createDraft(String json, String idempotencyKey) throws Exception {
        return read(mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String twoItemJson() {
        return orderJson("韩兆亮", apple(20, "箱", 68), banana(30, "件", 32));
    }

    private static String apple(int qty, String uom, double rate) {
        return line("APPLE-80", qty, uom, rate);
    }

    private static String banana(int qty, String uom, double rate) {
        return line("BANANA-FEN", qty, uom, rate);
    }

    private static String line(String itemCode, double qty, String uom, double rate) {
        return "{\"itemCode\":\"" + itemCode + "\",\"qty\":" + qty + ",\"uom\":\"" + uom + "\",\"rate\":" + rate + "}";
    }

    private static String orderJson(String customerId, String... items) {
        return "{\"customerId\":\"" + customerId + "\",\"transactionDate\":\"2026-08-19\",\"items\":["
                + String.join(",", items) + "]}";
    }

    private static String paymentJson(String orderId, int amount, String method) {
        return paymentJsonWithCustomer("韩兆亮", orderId, amount, method);
    }

    private static String paymentJsonWithCustomer(String customerId, String orderId, int amount, String method) {
        return "{\"customerId\":\"" + customerId + "\",\"relatedOrderId\":\"" + orderId
                + "\",\"amount\":" + amount + ",\"paymentMethodId\":\"" + method + "\"}";
    }

    private static java.util.List<String> itemCodes(JsonNode order) {
        java.util.List<String> codes = new java.util.ArrayList<>();
        order.path("items").forEach(item -> codes.add(item.path("itemCode").asText()));
        return codes;
    }

    private static java.math.BigDecimal qtyOf(JsonNode order, String itemCode) {
        for (JsonNode item : order.path("items")) {
            if (itemCode.equals(item.path("itemCode").asText())) {
                return item.path("qty").decimalValue();
            }
        }
        throw new AssertionError(itemCode);
    }
}
