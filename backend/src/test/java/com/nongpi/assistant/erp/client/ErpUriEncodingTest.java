package com.nongpi.assistant.erp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.erp.dto.ErpCustomer;
import com.nongpi.assistant.erp.dto.ErpUomConversion;
import com.nongpi.assistant.erp.support.FakeErpNext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * URL 编码回归测试。
 *
 * <p>来源：Phase 1B 真实 ERPNext Smoke Test。
 * 当时路径片段用 form 编码（空格变 {@code +}），真实 Frappe 把
 * {@code /api/resource/UOM+Conversion+Detail} 当成未知 DocType 并返回 404，
 * 导致商品与库存接口整体不可用。带空格的 DocType 名必须编码成 {@code %20}。
 */
@DisplayName("ERPNext URL 编码")
class ErpUriEncodingTest {

    private FakeErpNext erp;
    private ErpRestClient client;

    @BeforeEach
    void setUp() throws IOException {
        erp = new FakeErpNext();
        erp.start();
        client = new ErpRestClient(new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        erp.close();
    }

    @Test
    @DisplayName("带空格的 DocType 在路径中编码为 %20，不能编码成 +")
    void encodesDoctypeSpacesAsPercent20() {
        assertThat(ErpRestClient.encodePathSegment("UOM Conversion Detail"))
                .isEqualTo("UOM%20Conversion%20Detail");
        assertThat(ErpRestClient.encodePathSegment("Item Price"))
                .isEqualTo("Item%20Price");
    }

    @Test
    @DisplayName("查询参数仍用 form 编码，空格为 + 是合法的")
    void encodesQueryValuesAsForm() {
        assertThat(ErpRestClient.encodeQueryValue("item_name asc")).isEqualTo("item_name+asc");
    }

    @Test
    @DisplayName("子表查询命中真实 DocType 名，不会因为路径编码被当成未知 DocType")
    void listRequestReachesSpacedDoctype() {
        erp.onList(ErpUomConversion.DOCTYPE, """
                {"data": [{"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1}]}
                """);

        List<ErpUomConversion> rows = client.list(erp.connection("T001"), ErpUomConversion.DOCTYPE,
                ErpQuery.create().fields("parent", "uom").parent("Item").unlimited(),
                ErpUomConversion.class);

        assertThat(rows).hasSize(1);
        assertThat(erp.requests().get(0).getPath()).contains("UOM%20Conversion%20Detail");
        assertThat(erp.requests().get(0).getPath()).doesNotContain("UOM+Conversion+Detail");
    }

    @Test
    @DisplayName("客户主键含中文时路径正确编码")
    void encodesChineseDocName() {
        // 实测 ERPNext 默认按客户名命名 Customer，主键就是中文
        erp.onDoc(ErpCustomer.DOCTYPE, "韩兆亮",
                "{\"data\": {\"name\": \"韩兆亮\", \"customer_name\": \"韩兆亮\"}}");

        assertThat(client.getDoc(erp.connection("T001"), ErpCustomer.DOCTYPE, "韩兆亮", ErpCustomer.class))
                .isPresent();
        assertThat(erp.requests().get(0).getPath()).contains("%E9%9F%A9%E5%85%86%E4%BA%AE");
    }

    @Test
    @DisplayName("Fake 对未知 DocType 返回 404，与真实 Frappe 一致")
    void fakeRejectsUnknownDoctype() {
        // 若客户端把路径编错，Fake 不能替它兜底成空列表
        assertThat(erp.requests()).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        client.list(erp.connection("T001"), "Not A Real Doctype",
                                ErpQuery.create().fields("name"), ErpCustomer.class))
                .isInstanceOf(com.nongpi.assistant.common.error.BusinessException.class);
    }
}
