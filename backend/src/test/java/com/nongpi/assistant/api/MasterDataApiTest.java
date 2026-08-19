package com.nongpi.assistant.api;

import com.nongpi.assistant.erp.dto.ErpBin;
import com.nongpi.assistant.erp.dto.ErpCustomer;
import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpItemPrice;
import com.nongpi.assistant.erp.dto.ErpItemReorder;
import com.nongpi.assistant.erp.dto.ErpUomConversion;
import com.nongpi.assistant.erp.support.FakeErpNext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 主数据只读 API 的契约测试。
 *
 * <p>两个租户分别指向两台独立的假 ERPNext，用来验证租户隔离：
 * 这个结构同时覆盖了「每租户一套 ERPNext」的部署形态，
 * 也不妨碍将来两个租户共用同一个 baseUrl（AGENTS.md #20 的决策仍然开放）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("主数据只读 API")
class MasterDataApiTest {

    private static final String TOKEN_A = "token-tenant-a";
    private static final String TOKEN_B = "token-tenant-b";

    private static final FakeErpNext ERP_A = start();
    private static final FakeErpNext ERP_B = start();

    @Autowired
    private MockMvc mockMvc;

    private static FakeErpNext start() {
        try {
            FakeErpNext erp = new FakeErpNext();
            erp.start();
            return erp;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void erpProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tenants[0].tenant-id", () -> "T001");
        registry.add("app.tenants[0].tenant-name", () -> "徐州水果档口");
        registry.add("app.tenants[0].access-tokens[0]", () -> TOKEN_A);
        registry.add("app.tenants[0].erp.base-url", ERP_A::baseUrl);
        registry.add("app.tenants[0].erp.api-key", () -> "key-a");
        registry.add("app.tenants[0].erp.api-secret", () -> "secret-a");
        registry.add("app.tenants[0].erp.selling-price-list", () -> "Standard Selling");
        registry.add("app.tenants[0].erp.connect-timeout", () -> "500ms");
        registry.add("app.tenants[0].erp.read-timeout", () -> "500ms");

        registry.add("app.tenants[1].tenant-id", () -> "T002");
        registry.add("app.tenants[1].tenant-name", () -> "广州批发档口");
        registry.add("app.tenants[1].access-tokens[0]", () -> TOKEN_B);
        registry.add("app.tenants[1].erp.base-url", ERP_B::baseUrl);
        registry.add("app.tenants[1].erp.api-key", () -> "key-b");
        registry.add("app.tenants[1].erp.api-secret", () -> "secret-b");
        registry.add("app.tenants[1].erp.selling-price-list", () -> "Standard Selling");
        registry.add("app.tenants[1].erp.connect-timeout", () -> "500ms");
        registry.add("app.tenants[1].erp.read-timeout", () -> "500ms");
    }

    @BeforeEach
    void resetFakes() {
        ERP_A.reset();
        ERP_B.reset();
    }

    @AfterAll
    static void shutdown() throws IOException {
        ERP_A.close();
        ERP_B.close();
    }

    @Test
    @DisplayName("没有 Access Token 时拒绝访问")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(ERP_A.requests()).isEmpty();
        assertThat(ERP_B.requests()).isEmpty();
    }

    @Test
    @DisplayName("客户端自己声明 tenantId 不产生任何效果")
    void ignoresClientSuppliedTenantId() throws Exception {
        // 只带 tenantId 不带 token：不能通过
        mockMvc.perform(get("/api/v1/customers").header("X-Tenant-Id", "T001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        ERP_A.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"A-CUST\", \"customer_name\": \"甲租户客户\"}]}");
        ERP_B.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"B-CUST\", \"customer_name\": \"乙租户客户\"}]}");

        // 用 A 的 token 但声称自己是 T002：仍然只能看到 A 的数据
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("X-Tenant-Id", "T002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId").value("A-CUST"));

        assertThat(ERP_B.requests()).isEmpty();
    }

    @Test
    @DisplayName("租户隔离：不同 Access Token 只能访问自己租户的 ERPNext")
    void isolatesTenants() throws Exception {
        ERP_A.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"A-CUST\", \"customer_name\": \"甲租户客户\"}]}");
        ERP_B.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"B-CUST\", \"customer_name\": \"乙租户客户\"}]}");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("甲租户客户"));

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("乙租户客户"));

        assertThat(ERP_A.requests()).hasSize(1);
        assertThat(ERP_B.requests()).hasSize(1);
        assertThat(ERP_A.requests().get(0).getHeader("Authorization")).isEqualTo("token key-a:secret-a");
        assertThat(ERP_B.requests().get(0).getHeader("Authorization")).isEqualTo("token key-b:secret-b");
    }

    @Test
    @DisplayName("无效 Access Token 被拒绝")
    void rejectsUnknownToken() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer 伪造的令牌"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("GET /api/v1/customers 返回来自 ERPNext 的客户字段")
    void returnsCustomerList() throws Exception {
        ERP_A.onList(ErpCustomer.DOCTYPE, """
                {"data": [{
                  "name": "CUST-001", "customer_name": "韩兆亮", "mobile_no": "13800003456",
                  "primary_address": "徐州市雨润市场 A12", "disabled": 0
                }]}
                """);

        mockMvc.perform(get("/api/v1/customers").param("q", "韩").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId").value("CUST-001"))
                .andExpect(jsonPath("$.content[0].customerName").value("韩兆亮"))
                .andExpect(jsonPath("$.content[0].phone").value("13800003456"))
                .andExpect(jsonPath("$.content[0].address").value("徐州市雨润市场 A12"))
                .andExpect(jsonPath("$.content[0].aliases").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/customers 支持分页并给出 hasMore")
    void paginatesCustomerList() throws Exception {
        ERP_A.onList(ErpCustomer.DOCTYPE, """
                {"data": [
                  {"name": "C1", "customer_name": "客户一"},
                  {"name": "C2", "customer_name": "客户二"},
                  {"name": "C3", "customer_name": "客户三"}
                ]}
                """);

        mockMvc.perform(get("/api/v1/customers")
                        .param("page", "1")
                        .param("pageSize", "2")
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    @DisplayName("非法 pageSize 返回 INVALID_REQUEST 而不是 500")
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .param("pageSize", "9999")
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/v1/customers/{id} 客户不存在时返回 CUSTOMER_NOT_FOUND")
    void returnsCustomerNotFound() throws Exception {
        ERP_A.onDocStatus(ErpCustomer.DOCTYPE, "CUST-404", 404, "{\"exception\": \"DoesNotExistError\"}");

        mockMvc.perform(get("/api/v1/customers/CUST-404").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.details.customerId").value("CUST-404"));
    }

    @Test
    @DisplayName("GET /api/v1/customers/{id} 返回客户详情")
    void returnsCustomerDetail() throws Exception {
        ERP_A.onDoc(ErpCustomer.DOCTYPE, "CUST-001", """
                {"data": {"name": "CUST-001", "customer_name": "韩兆亮", "mobile_no": "13800003456"}}
                """);

        mockMvc.perform(get("/api/v1/customers/CUST-001").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-001"))
                .andExpect(jsonPath("$.customerName").value("韩兆亮"));
    }

    @Test
    @DisplayName("GET /api/v1/customers/selector 的 recent 为空数组，不伪造最近交易客户")
    void returnsCustomerSelector() throws Exception {
        ERP_A.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"CUST-001\", \"customer_name\": \"韩兆亮\"}]}");

        mockMvc.perform(get("/api/v1/customers/selector").param("q", "老韩")
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent").isEmpty())
                .andExpect(jsonPath("$.results[0].customerId").value("CUST-001"));
    }

    @Test
    @DisplayName("GET /api/v1/products/selector 返回商品身份、合法单位与参考价格")
    void returnsProductSelector() throws Exception {
        ERP_A.onList(ErpItem.DOCTYPE, """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                  "stock_uom": "箱", "sales_uom": "箱", "variant_of": "APPLE",
                  "has_variants": 0, "disabled": 0}]}
                """);
        ERP_A.onList(ErpUomConversion.DOCTYPE, """
                {"data": [
                  {"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1},
                  {"parent": "APPLE-80", "uom": "斤", "conversion_factor": 20.0, "idx": 2}
                ]}
                """);
        ERP_A.onList(ErpItemAttribute.DOCTYPE, """
                {"data": [{"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1}]}
                """);
        ERP_A.onList(ErpItemPrice.DOCTYPE, """
                {"data": [
                  {"name": "IP-1", "item_code": "APPLE-80", "price_list": "Standard Selling",
                   "price_list_rate": 68.0, "currency": "CNY", "uom": "箱"},
                  {"name": "IP-2", "item_code": "APPLE-80", "price_list": "Standard Selling",
                   "price_list_rate": 3.8, "currency": "CNY", "uom": "斤"}
                ]}
                """);

        mockMvc.perform(get("/api/v1/products/selector").param("q", "八零")
                        .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                // 常买商品需要订单历史，本轮为空数组
                .andExpect(jsonPath("$.frequentItems").isEmpty())
                // productId 只做商品族分组，itemCode 才是可交易 Item 的正式身份
                .andExpect(jsonPath("$.results[0].productId").value("APPLE"))
                .andExpect(jsonPath("$.results[0].itemCode").value("APPLE-80"))
                // 不再暴露 ERPNext 里不存在的 variantId
                .andExpect(jsonPath("$.results[0].variantId").doesNotExist())
                .andExpect(jsonPath("$.results[0].productName").value("苹果80果"))
                .andExpect(jsonPath("$.results[0].spec").value("80mm"))
                .andExpect(jsonPath("$.results[0].defaultUom").value("箱"))
                .andExpect(jsonPath("$.results[0].referencePrice").value(68.0))
                .andExpect(jsonPath("$.results[0].priceUom").value("箱"))
                .andExpect(jsonPath("$.results[0].allowedUoms.length()").value(2))
                .andExpect(jsonPath("$.results[0].allowedUoms[0].uom").value("箱"))
                .andExpect(jsonPath("$.results[0].allowedUoms[0].referencePrice").value(68.0))
                .andExpect(jsonPath("$.results[0].allowedUoms[1].uom").value("斤"))
                .andExpect(jsonPath("$.results[0].allowedUoms[1].conversionFactor").value(20.0))
                .andExpect(jsonPath("$.results[0].allowedUoms[1].referencePrice").value(3.8));
    }

    @Test
    @DisplayName("GET /api/v1/inventory 返回来自 ERPNext 的库存数量与库存单位")
    void returnsInventory() throws Exception {
        ERP_A.onList(ErpBin.DOCTYPE, """
                {"data": [{"item_code": "APPLE-80", "warehouse": "主仓库 - T",
                  "actual_qty": 450.0, "stock_uom": "箱"}]}
                """);
        ERP_A.onList(ErpItem.DOCTYPE, """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                  "stock_uom": "箱", "variant_of": "APPLE"}]}
                """);
        ERP_A.onList(ErpItemAttribute.DOCTYPE, """
                {"data": [{"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1}]}
                """);
        ERP_A.onList(ErpItemReorder.DOCTYPE, "{\"data\": []}");

        mockMvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemCode").value("APPLE-80"))
                .andExpect(jsonPath("$.content[0].productId").value("APPLE"))
                .andExpect(jsonPath("$.content[0].variantId").doesNotExist())
                .andExpect(jsonPath("$.content[0].productName").value("苹果80果"))
                .andExpect(jsonPath("$.content[0].spec").value("80mm"))
                .andExpect(jsonPath("$.content[0].quantity").value(450.0))
                .andExpect(jsonPath("$.content[0].stockUom").value("箱"))
                .andExpect(jsonPath("$.content[0].warehouse").value("主仓库 - T"))
                // 没有预警配置时不返回 lowStock / alertQty，也不返回任何库存百分比
                .andExpect(jsonPath("$.content[0].lowStock").doesNotExist())
                .andExpect(jsonPath("$.content[0].alertQty").doesNotExist())
                .andExpect(jsonPath("$.content[0].stockPercent").doesNotExist());
    }

    @Test
    @DisplayName("ERPNext 不可用时返回 ERP_UNAVAILABLE，不返回成功也不泄露内部细节")
    void returnsErpUnavailable() throws Exception {
        ERP_A.onListStatus(ErpCustomer.DOCTYPE, 500,
                "{\"exception\": \"pymysql.err.ProgrammingError: tabCustomer\"}");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ERP_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("ERP 系统暂时不可用"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("健康检查不需要 Access Token")
    void healthEndpointStaysOpen() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
