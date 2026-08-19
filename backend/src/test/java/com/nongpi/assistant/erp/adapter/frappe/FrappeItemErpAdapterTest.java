package com.nongpi.assistant.erp.adapter.frappe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.mapper.ProductErpMapper;
import com.nongpi.assistant.erp.support.FakeErpNext;
import com.nongpi.assistant.product.domain.AllowedUom;
import com.nongpi.assistant.product.domain.ProductVariant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ERPNext 商品与单位映射")
class FrappeItemErpAdapterTest {

    private FakeErpNext erp;
    private FrappeItemErpAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        erp = new FakeErpNext();
        erp.start();
        adapter = new FrappeItemErpAdapter(new ErpRestClient(new ObjectMapper()), new ProductErpMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        erp.close();
    }

    @Test
    @DisplayName("非变体商品：productId 回落为自身 item_code，规格为空")
    void mapsPlainItem() {
        erp.onList("Item", """
                {"data": [{
                  "name": "GRAPE-01",
                  "item_code": "GRAPE-01",
                  "item_name": "阳光玫瑰",
                  "stock_uom": "箱",
                  "sales_uom": null,
                  "variant_of": null,
                  "has_variants": 0,
                  "disabled": 0
                }]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [{"parent": "GRAPE-01", "uom": "箱", "conversion_factor": 1.0, "idx": 1}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Price", "{\"data\": []}");

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.itemCode()).isEqualTo("GRAPE-01");
        assertThat(variant.variantId()).isEqualTo("GRAPE-01");
        // ERPNext 中非变体商品没有模板，productId 只能回落为自身编码
        assertThat(variant.productId()).isEqualTo("GRAPE-01");
        assertThat(variant.productName()).isEqualTo("阳光玫瑰");
        assertThat(variant.spec()).isNull();
        assertThat(variant.defaultUom()).isEqualTo("箱");
    }

    @Test
    @DisplayName("变体商品：productId 取 variant_of，规格取变体属性值")
    void mapsVariantItem() {
        erp.onList("Item", """
                {"data": [{
                  "name": "APPLE-80",
                  "item_code": "APPLE-80",
                  "item_name": "苹果80果",
                  "stock_uom": "箱",
                  "sales_uom": "箱",
                  "variant_of": "APPLE",
                  "has_variants": 0,
                  "disabled": 0
                }]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [{"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1}]}
                """);
        erp.onList("Item Variant Attribute", """
                {"data": [{"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1}]}
                """);
        erp.onList("Item Price", "{\"data\": []}");

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.productId()).isEqualTo("APPLE");
        // ERPNext 中 Item.name == Item.item_code，不存在独立的变体主键
        assertThat(variant.variantId()).isEqualTo("APPLE-80");
        assertThat(variant.itemCode()).isEqualTo("APPLE-80");
        assertThat(variant.spec()).isEqualTo("80mm");
    }

    @Test
    @DisplayName("多个变体属性按 idx 顺序拼成规格")
    void joinsMultipleAttributesIntoSpec() {
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80-RED", "item_code": "APPLE-80-RED", "item_name": "红富士80果",
                  "stock_uom": "箱", "variant_of": "APPLE", "has_variants": 0, "disabled": 0}]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [{"parent": "APPLE-80-RED", "uom": "箱", "conversion_factor": 1.0, "idx": 1}]}
                """);
        erp.onList("Item Variant Attribute", """
                {"data": [
                  {"parent": "APPLE-80-RED", "attribute": "颜色", "attribute_value": "红", "idx": 2},
                  {"parent": "APPLE-80-RED", "attribute": "果径", "attribute_value": "80mm", "idx": 1}
                ]}
                """);
        erp.onList("Item Price", "{\"data\": []}");

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.spec()).isEqualTo("80mm / 红");
    }

    @Test
    @DisplayName("单单位商品：allowedUoms 只有一项，前端据此只读展示")
    void mapsSingleUomItem() {
        erp.onList("Item", """
                {"data": [{"name": "BANANA-FEN", "item_code": "BANANA-FEN", "item_name": "香蕉粉蕉",
                  "stock_uom": "件", "sales_uom": "件", "has_variants": 0, "disabled": 0}]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [{"parent": "BANANA-FEN", "uom": "件", "conversion_factor": 1.0, "idx": 1}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Price", """
                {"data": [{"name": "IP-1", "item_code": "BANANA-FEN", "price_list": "Standard Selling",
                  "price_list_rate": 32.0, "currency": "CNY", "uom": "件"}]}
                """);

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.allowedUoms()).hasSize(1);
        assertThat(variant.allowedUoms().get(0).uom()).isEqualTo("件");
        assertThat(variant.defaultUom()).isEqualTo("件");
        assertThat(variant.referencePrice()).isEqualByComparingTo("32.0");
        assertThat(variant.priceUom()).isEqualTo("件");
        assertThat(variant.currency()).isEqualTo("CNY");
    }

    @Test
    @DisplayName("多单位商品：每个单位带自己的换算率与参考价，价格不跨单位复用")
    void mapsMultiUomItemWithPricePerUom() {
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                  "stock_uom": "箱", "sales_uom": "箱", "variant_of": "APPLE", "has_variants": 0, "disabled": 0}]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [
                  {"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1},
                  {"parent": "APPLE-80", "uom": "斤", "conversion_factor": 20.0, "idx": 2}
                ]}
                """);
        erp.onList("Item Variant Attribute", """
                {"data": [{"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1}]}
                """);
        erp.onList("Item Price", """
                {"data": [
                  {"name": "IP-1", "item_code": "APPLE-80", "price_list": "Standard Selling",
                   "price_list_rate": 68.0, "currency": "CNY", "uom": "箱"},
                  {"name": "IP-2", "item_code": "APPLE-80", "price_list": "Standard Selling",
                   "price_list_rate": 3.8, "currency": "CNY", "uom": "斤"}
                ]}
                """);

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.allowedUoms()).hasSize(2);
        AllowedUom box = uom(variant, "箱");
        AllowedUom jin = uom(variant, "斤");
        assertThat(box.conversionFactor()).isEqualByComparingTo("1.0");
        assertThat(box.referencePrice()).isEqualByComparingTo("68.0");
        assertThat(jin.conversionFactor()).isEqualByComparingTo("20.0");
        // 切到斤必须是斤价，不能沿用箱价
        assertThat(jin.referencePrice()).isEqualByComparingTo("3.8");
        assertThat(variant.referencePrice()).isEqualByComparingTo("68.0");
        assertThat(variant.priceUom()).isEqualTo("箱");
    }

    @Test
    @DisplayName("某个单位在 ERPNext 中没有价格时该单位的参考价为空，不借用其他单位价格")
    void leavesUomPriceNullWhenNotPriced() {
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                  "stock_uom": "箱", "sales_uom": "箱", "has_variants": 0, "disabled": 0}]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [
                  {"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1},
                  {"parent": "APPLE-80", "uom": "斤", "conversion_factor": 20.0, "idx": 2}
                ]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Price", """
                {"data": [{"name": "IP-1", "item_code": "APPLE-80", "price_list": "Standard Selling",
                  "price_list_rate": 68.0, "currency": "CNY", "uom": "箱"}]}
                """);

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(uom(variant, "箱").referencePrice()).isEqualByComparingTo("68.0");
        assertThat(uom(variant, "斤").referencePrice()).isNull();
    }

    @Test
    @DisplayName("未配置销售价格表时不查价格，参考价为空而不是猜一个价格表")
    void skipsPriceLookupWithoutPriceList() {
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                  "stock_uom": "箱", "has_variants": 0, "disabled": 0}]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [{"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");

        ProductVariant variant = adapter.search(erp.connection("T001", null), null, 0, 30).get(0);

        assertThat(variant.referencePrice()).isNull();
        assertThat(variant.priceUom()).isNull();
        assertThat(erp.firstRequestFor("Item Price")).isEmpty();
    }

    @Test
    @DisplayName("ERP 缺少 uoms 子表时按库存单位补一个合法单位，避免商品无法进入订单")
    void fallsBackToStockUomWhenChildTableMissing() {
        erp.onList("Item", """
                {"data": [{"name": "PEAR-01", "item_code": "PEAR-01", "item_name": "梨", "stock_uom": "筐",
                  "has_variants": 0, "disabled": 0}]}
                """);
        erp.onList("UOM Conversion Detail", "{\"data\": []}");
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Price", "{\"data\": []}");

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.allowedUoms()).hasSize(1);
        assertThat(variant.allowedUoms().get(0).uom()).isEqualTo("筐");
        assertThat(variant.allowedUoms().get(0).conversionFactor()).isEqualByComparingTo("1");
        assertThat(variant.defaultUom()).isEqualTo("筐");
    }

    @Test
    @DisplayName("ERP 返回缺字段的商品时不抛异常，缺失字段为 null")
    void toleratesMissingItemFields() {
        // 只有主键：没有 item_name，没有 stock_uom，没有任何子表
        erp.onList("Item", "{\"data\": [{\"name\": \"UNKNOWN-1\"}]}");
        erp.onList("UOM Conversion Detail", "{\"data\": []}");
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Price", "{\"data\": []}");

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.itemCode()).isEqualTo("UNKNOWN-1");
        assertThat(variant.productName()).isEqualTo("UNKNOWN-1");
        assertThat(variant.spec()).isNull();
        assertThat(variant.defaultUom()).isNull();
        assertThat(variant.allowedUoms()).isEmpty();
        assertThat(variant.referencePrice()).isNull();
    }

    @Test
    @DisplayName("多个商品的子表一次批量查回，不产生 N+1 请求")
    void batchesChildTableQueries() {
        erp.onList("Item", """
                {"data": [
                  {"name": "APPLE-70", "item_code": "APPLE-70", "item_name": "苹果70果", "stock_uom": "箱",
                   "variant_of": "APPLE", "has_variants": 0, "disabled": 0},
                  {"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果", "stock_uom": "箱",
                   "variant_of": "APPLE", "has_variants": 0, "disabled": 0},
                  {"name": "APPLE-85", "item_code": "APPLE-85", "item_name": "苹果85果", "stock_uom": "箱",
                   "variant_of": "APPLE", "has_variants": 0, "disabled": 0}
                ]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [
                  {"parent": "APPLE-70", "uom": "箱", "conversion_factor": 1.0, "idx": 1},
                  {"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1},
                  {"parent": "APPLE-85", "uom": "箱", "conversion_factor": 1.0, "idx": 1}
                ]}
                """);
        erp.onList("Item Variant Attribute", """
                {"data": [
                  {"parent": "APPLE-70", "attribute": "果径", "attribute_value": "70mm", "idx": 1},
                  {"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1},
                  {"parent": "APPLE-85", "attribute": "果径", "attribute_value": "85mm", "idx": 1}
                ]}
                """);
        erp.onList("Item Price", "{\"data\": []}");

        List<ProductVariant> variants = adapter.search(erp.connection("T001"), null, 0, 30);

        assertThat(variants).extracting(ProductVariant::spec).containsExactly("70mm", "80mm", "85mm");
        // Item 1 次 + 三个批量子查询，总共 4 次；商品数增加不会增加请求数
        assertThat(erp.requests()).hasSize(4);
        assertThat(erp.decodedQueryFor("UOM Conversion Detail")).contains("parent=Item");
        assertThat(erp.decodedQueryFor("UOM Conversion Detail"))
                .contains("[\"parent\",\"in\",[\"APPLE-70\",\"APPLE-80\",\"APPLE-85\"]]");
    }

    @Test
    @DisplayName("列表查询排除已停用商品与模板商品")
    void excludesDisabledAndTemplateItems() {
        erp.onList("Item", "{\"data\": []}");

        adapter.search(erp.connection("T001"), null, 0, 30);

        String query = erp.decodedQueryFor("Item");
        assertThat(query).contains("[\"disabled\",\"=\",0]");
        assertThat(query).contains("[\"is_sales_item\",\"=\",1]");
        assertThat(query).contains("[\"has_variants\",\"=\",0]");
    }

    @Test
    @DisplayName("支持按规格搜索：先在变体属性中反查 item_code")
    void searchesBySpec() {
        erp.onList("Item Variant Attribute", """
                {"data": [{"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1}]}
                """);
        erp.onList("Item", "{\"data\": []}");

        adapter.search(erp.connection("T001"), "80mm", 0, 30);

        assertThat(erp.decodedQueryFor("Item Variant Attribute"))
                .contains("[\"attribute_value\",\"like\",\"%80mm%\"]");
        assertThat(erp.decodedQueryFor("Item")).contains("[\"name\",\"in\",[\"APPLE-80\"]]");
    }

    @Test
    @DisplayName("ERPNext 无响应时映射为 ERP_UNAVAILABLE，不泄露内部异常")
    void mapsTimeoutToErpUnavailable() {
        erp.hangOnEveryRequest();

        assertThatThrownBy(() -> adapter.search(erp.connection("T001"), null, 0, 30))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(BusinessErrorCode.ERP_UNAVAILABLE);
    }

    @Test
    @DisplayName("按 item_code 查询：ERPNext 返回 404 时视为商品不存在")
    void returnsEmptyWhenItemMissing() {
        erp.onDocStatus("Item", "APPLE-99", 404, "{\"exception\": \"DoesNotExistError\"}");

        Optional<ProductVariant> variant = adapter.findByItemCode(erp.connection("T001"), "APPLE-99");

        assertThat(variant).isEmpty();
    }

    @Test
    @DisplayName("sales_uom 不在合法单位列表时回落到库存单位，不把非法单位当默认值")
    void fallsBackWhenSalesUomNotAllowed() {
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                  "stock_uom": "斤", "sales_uom": "袋", "has_variants": 0, "disabled": 0}]}
                """);
        erp.onList("UOM Conversion Detail", """
                {"data": [{"parent": "APPLE-80", "uom": "斤", "conversion_factor": 1.0, "idx": 1}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Price", "{\"data\": []}");

        ProductVariant variant = adapter.search(erp.connection("T001"), null, 0, 30).get(0);

        assertThat(variant.defaultUom()).isEqualTo("斤");
        assertThat(variant.allowedUoms()).extracting(AllowedUom::uom).containsExactly("斤");
    }

    private AllowedUom uom(ProductVariant variant, String uom) {
        return variant.allowedUoms().stream()
                .filter(candidate -> uom.equals(candidate.uom()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到单位 " + uom));
    }
}
