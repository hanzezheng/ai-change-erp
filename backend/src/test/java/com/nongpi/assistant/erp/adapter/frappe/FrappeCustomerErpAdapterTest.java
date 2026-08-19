package com.nongpi.assistant.erp.adapter.frappe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.customer.domain.CustomerSummary;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.mapper.CustomerErpMapper;
import com.nongpi.assistant.erp.support.FakeErpNext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ERPNext 客户映射")
class FrappeCustomerErpAdapterTest {

    private FakeErpNext erp;
    private FrappeCustomerErpAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        erp = new FakeErpNext();
        erp.start();
        adapter = new FrappeCustomerErpAdapter(new ErpRestClient(new ObjectMapper()), new CustomerErpMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        erp.close();
    }

    @Test
    @DisplayName("客户列表映射出 customerId / customerName / phone / address")
    void mapsCustomerList() {
        erp.onList("Customer", """
                {"data": [
                  {
                    "name": "CUST-001",
                    "customer_name": "韩兆亮",
                    "mobile_no": "13800003456",
                    "primary_address": "徐州市雨润农副产品批发市场 A12<br>江苏 徐州",
                    "customer_group": "批发客户",
                    "disabled": 0
                  },
                  {
                    "name": "CUST-002",
                    "customer_name": "韩兆良",
                    "mobile_no": "13900001111",
                    "primary_address": null,
                    "customer_group": "批发客户",
                    "disabled": 0
                  }
                ]}
                """);

        List<CustomerSummary> customers = adapter.search(erp.connection("T001"), null, 0, 20);

        assertThat(customers).hasSize(2);
        CustomerSummary first = customers.get(0);
        assertThat(first.customerId()).isEqualTo("CUST-001");
        assertThat(first.customerName()).isEqualTo("韩兆亮");
        assertThat(first.phone()).isEqualTo("13800003456");
        assertThat(first.address()).isEqualTo("徐州市雨润农副产品批发市场 A12, 江苏 徐州");
        // 称呼属于 SaaS Customer Identity，Adapter 不得从 ERPNext 字段凑出别名
        assertThat(first.aliases()).isEmpty();

        CustomerSummary second = customers.get(1);
        assertThat(second.customerId()).isEqualTo("CUST-002");
        assertThat(second.address()).isNull();
    }

    @Test
    @DisplayName("搜索关键字下发为 or_filters，并始终排除已停用客户")
    void sendsSearchFilters() {
        erp.onList("Customer", "{\"data\": []}");

        adapter.search(erp.connection("T001"), "老韩", 0, 20);

        String query = erp.decodedQueryFor("Customer");
        assertThat(query).contains("[[\"disabled\",\"=\",0]]");
        assertThat(query).contains("[\"customer_name\",\"like\",\"%老韩%\"]");
        assertThat(query).contains("[\"mobile_no\",\"like\",\"%老韩%\"]");
        assertThat(query).contains("limit_start=0");
        assertThat(query).contains("limit_page_length=20");
    }

    @Test
    @DisplayName("关键字中的 LIKE 通配符按字面量处理")
    void escapesLikeWildcards() {
        erp.onList("Customer", "{\"data\": []}");

        adapter.search(erp.connection("T001"), "100%", 0, 20);

        assertThat(erp.decodedQueryFor("Customer")).contains("%100\\\\%%");
    }

    @Test
    @DisplayName("按 ERP Customer ID 查询客户详情")
    void findsCustomerById() {
        erp.onDoc("Customer", "CUST-001", """
                {"data": {
                  "name": "CUST-001",
                  "customer_name": "韩兆亮",
                  "mobile_no": "13800003456",
                  "primary_address": "徐州市雨润农副产品批发市场 A12"
                }}
                """);

        Optional<CustomerSummary> customer = adapter.findById(erp.connection("T001"), "CUST-001");

        assertThat(customer).isPresent();
        assertThat(customer.get().customerId()).isEqualTo("CUST-001");
        assertThat(customer.get().customerName()).isEqualTo("韩兆亮");
    }

    @Test
    @DisplayName("ERPNext 返回 404 时视为客户不存在，交由上层翻译错误码")
    void returnsEmptyWhenCustomerMissing() {
        erp.onDocStatus("Customer", "CUST-404", 404,
                "{\"exception\": \"frappe.exceptions.DoesNotExistError: Customer CUST-404 not found\"}");

        Optional<CustomerSummary> customer = adapter.findById(erp.connection("T001"), "CUST-404");

        assertThat(customer).isEmpty();
    }

    @Test
    @DisplayName("ERP 只返回部分字段时不抛异常，缺失字段为 null")
    void toleratesMissingFields() {
        // customer_name / mobile_no / primary_address 全部缺失，只有主键
        erp.onList("Customer", "{\"data\": [{\"name\": \"CUST-003\"}]}");

        List<CustomerSummary> customers = adapter.search(erp.connection("T001"), null, 0, 20);

        assertThat(customers).hasSize(1);
        CustomerSummary customer = customers.get(0);
        assertThat(customer.customerId()).isEqualTo("CUST-003");
        // 名称缺失时回落到主键，保证 App 至少能显示一个标识
        assertThat(customer.customerName()).isEqualTo("CUST-003");
        assertThat(customer.phone()).isNull();
        assertThat(customer.address()).isNull();
    }
}
