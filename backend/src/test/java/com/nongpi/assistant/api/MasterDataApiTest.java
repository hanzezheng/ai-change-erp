package com.nongpi.assistant.api;

import com.nongpi.assistant.erp.dto.ErpBin;
import com.nongpi.assistant.erp.dto.ErpCustomer;
import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpItemPrice;
import com.nongpi.assistant.erp.dto.ErpItemReorder;
import com.nongpi.assistant.erp.dto.ErpUomConversion;
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

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("主数据只读 API")
class MasterDataApiTest extends AbstractSaasIntegrationTest {

    private static final FakeErpNext ERP_A = start();
    private static final FakeErpNext ERP_B = start();

    private String tokenA;
    private String tokenB;

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
    void seedTenantsAndTokens() {
        ERP_A.reset();
        ERP_B.reset();

        TenantEntity tenantA = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity tenantB = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity userA = newUser("owner-a", "password-a", UserStatus.ACTIVE);
        AppUserEntity userB = newUser("owner-b", "password-b", UserStatus.ACTIVE);
        MembershipEntity membershipA = newMembership(tenantA, userA, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        MembershipEntity membershipB = newMembership(tenantB, userB, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        newErpConnection(tenantA, ERP_A.baseUrl(), "key-a", "secret-a");
        newErpConnection(tenantB, ERP_B.baseUrl(), "key-b", "secret-b");
        tokenA = accessToken(userA, membershipA);
        tokenB = accessToken(userB, membershipB);
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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(ERP_A.requests()).isEmpty();
        assertThat(ERP_B.requests()).isEmpty();
    }

    @Test
    @DisplayName("客户端自己声明 tenantId 不产生任何效果")
    void ignoresClientSuppliedTenantId() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("X-Tenant-Id", "whatever"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        ERP_A.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"A-CUST\", \"customer_name\": \"甲租户客户\"}]}");
        ERP_B.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"B-CUST\", \"customer_name\": \"乙租户客户\"}]}");

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", bearer(tokenA))
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

        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("甲租户客户"));

        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer(tokenB)))
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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
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

        mockMvc.perform(get("/api/v1/customers").param("q", "韩").header("Authorization", bearer(tokenA)))
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
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    @DisplayName("非法 pageSize 返回 INVALID_REQUEST 而不是 500")
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .param("pageSize", "9999")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/v1/customers/{id} 客户不存在时返回 CUSTOMER_NOT_FOUND")
    void returnsCustomerNotFound() throws Exception {
        ERP_A.onDocStatus(ErpCustomer.DOCTYPE, "CUST-404", 404, "{\"exception\": \"DoesNotExistError\"}");

        mockMvc.perform(get("/api/v1/customers/CUST-404").header("Authorization", bearer(tokenA)))
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

        mockMvc.perform(get("/api/v1/customers/CUST-001").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-001"))
                .andExpect(jsonPath("$.customerName").value("韩兆亮"));
    }

    @Test
    @DisplayName("GET /api/v1/customers/selector 的 recent 为空数组，不伪造最近交易客户")
    void returnsCustomerSelector() throws Exception {
        ERP_A.onList(ErpCustomer.DOCTYPE, "{\"data\": [{\"name\": \"CUST-001\", \"customer_name\": \"韩兆亮\"}]}");

        mockMvc.perform(get("/api/v1/customers/selector").param("q", "老韩")
                        .header("Authorization", bearer(tokenA)))
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
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frequentItems").isEmpty())
                .andExpect(jsonPath("$.results[0].productId").value("APPLE"))
                .andExpect(jsonPath("$.results[0].itemCode").value("APPLE-80"))
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

        mockMvc.perform(get("/api/v1/inventory").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemCode").value("APPLE-80"))
                .andExpect(jsonPath("$.content[0].productId").value("APPLE"))
                .andExpect(jsonPath("$.content[0].variantId").doesNotExist())
                .andExpect(jsonPath("$.content[0].productName").value("苹果80果"))
                .andExpect(jsonPath("$.content[0].spec").value("80mm"))
                .andExpect(jsonPath("$.content[0].quantity").value(450.0))
                .andExpect(jsonPath("$.content[0].stockUom").value("箱"))
                .andExpect(jsonPath("$.content[0].warehouse").value("主仓库 - T"))
                .andExpect(jsonPath("$.content[0].lowStock").doesNotExist())
                .andExpect(jsonPath("$.content[0].alertQty").doesNotExist())
                .andExpect(jsonPath("$.content[0].stockPercent").doesNotExist());
    }

    @Test
    @DisplayName("ERPNext 不可用时返回 ERP_UNAVAILABLE，不返回成功也不泄露内部细节")
    void returnsErpUnavailable() throws Exception {
        ERP_A.onListStatus(ErpCustomer.DOCTYPE, 500,
                "{\"exception\": \"pymysql.err.ProgrammingError: tabCustomer\"}");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer(tokenA)))
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
