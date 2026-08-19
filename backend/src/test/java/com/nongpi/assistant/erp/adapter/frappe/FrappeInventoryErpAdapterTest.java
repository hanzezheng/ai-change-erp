package com.nongpi.assistant.erp.adapter.frappe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.mapper.InventoryErpMapper;
import com.nongpi.assistant.erp.support.FakeErpNext;
import com.nongpi.assistant.inventory.domain.InventoryItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ERPNext 库存映射")
class FrappeInventoryErpAdapterTest {

    private FakeErpNext erp;
    private FrappeInventoryErpAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        erp = new FakeErpNext();
        erp.start();
        adapter = new FrappeInventoryErpAdapter(new ErpRestClient(new ObjectMapper()), new InventoryErpMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        erp.close();
    }

    @Test
    @DisplayName("库存行映射出 itemCode / quantity / stockUom / warehouse 与规格")
    void mapsInventoryRow() {
        erp.onList("Bin", """
                {"data": [{"item_code": "APPLE-80", "warehouse": "主仓库 - T",
                  "actual_qty": 450.0, "stock_uom": "箱"}]}
                """);
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                  "stock_uom": "箱", "variant_of": "APPLE"}]}
                """);
        erp.onList("Item Variant Attribute", """
                {"data": [{"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1}]}
                """);
        erp.onList("Item Reorder", "{\"data\": []}");

        InventoryItem item = adapter.search(erp.connection("T001"), null, null, false, 0, 20).get(0);

        assertThat(item.itemCode()).isEqualTo("APPLE-80");
        assertThat(item.productId()).isEqualTo("APPLE");
        assertThat(item.productName()).isEqualTo("苹果80果");
        assertThat(item.spec()).isEqualTo("80mm");
        assertThat(item.quantity()).isEqualByComparingTo("450.0");
        assertThat(item.stockUom()).isEqualTo("箱");
        assertThat(item.warehouse()).isEqualTo("主仓库 - T");
    }

    @Test
    @DisplayName("没有预警配置时 alertQty 与 lowStock 都为空，不编造预警值")
    void leavesLowStockUnknownWithoutAlertConfig() {
        erp.onList("Bin", """
                {"data": [{"item_code": "APPLE-80", "warehouse": "主仓库 - T",
                  "actual_qty": 3.0, "stock_uom": "箱"}]}
                """);
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果", "stock_uom": "箱"}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Reorder", "{\"data\": []}");

        InventoryItem item = adapter.search(erp.connection("T001"), null, null, false, 0, 20).get(0);

        assertThat(item.alertQty()).isNull();
        // 数量很低，但 ERPNext 没有预警配置，系统不能自行判断「低库存」
        assertThat(item.lowStock()).isNull();
    }

    @Test
    @DisplayName("配置了仓库补货预警线时按该仓库的预警线判断低库存")
    void usesWarehouseReorderLevel() {
        erp.onList("Bin", """
                {"data": [
                  {"item_code": "APPLE-80", "warehouse": "主仓库 - T", "actual_qty": 30.0, "stock_uom": "箱"},
                  {"item_code": "APPLE-80", "warehouse": "备用仓 - T", "actual_qty": 200.0, "stock_uom": "箱"}
                ]}
                """);
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果", "stock_uom": "箱"}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Reorder", """
                {"data": [
                  {"parent": "APPLE-80", "warehouse": "主仓库 - T", "warehouse_reorder_level": 50.0},
                  {"parent": "APPLE-80", "warehouse": "备用仓 - T", "warehouse_reorder_level": 50.0}
                ]}
                """);

        List<InventoryItem> items = adapter.search(erp.connection("T001"), null, null, false, 0, 20);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).alertQty()).isEqualByComparingTo("50.0");
        assertThat(items.get(0).lowStock()).isTrue();
        assertThat(items.get(1).lowStock()).isFalse();
    }

    @Test
    @DisplayName("没有仓库级预警线时退到 Item 上的安全库存")
    void fallsBackToSafetyStock() {
        erp.onList("Bin", """
                {"data": [{"item_code": "BANANA-FEN", "warehouse": "主仓库 - T",
                  "actual_qty": 5.0, "stock_uom": "件"}]}
                """);
        erp.onList("Item", """
                {"data": [{"name": "BANANA-FEN", "item_code": "BANANA-FEN", "item_name": "香蕉粉蕉",
                  "stock_uom": "件", "safety_stock": 20.0}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Reorder", "{\"data\": []}");

        InventoryItem item = adapter.search(erp.connection("T001"), null, null, false, 0, 20).get(0);

        assertThat(item.alertQty()).isEqualByComparingTo("20.0");
        assertThat(item.lowStock()).isTrue();
    }

    @Test
    @DisplayName("低库存筛选先把候选限定在配置了预警线的商品上")
    void lowStockFilterRestrictsToAlertConfiguredItems() {
        erp.onList("Item Reorder", """
                {"data": [{"parent": "APPLE-80", "warehouse": "主仓库 - T", "warehouse_reorder_level": 50.0}]}
                """);
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果", "stock_uom": "箱"}]}
                """);
        erp.onList("Bin", """
                {"data": [{"item_code": "APPLE-80", "warehouse": "主仓库 - T",
                  "actual_qty": 30.0, "stock_uom": "箱"}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");

        List<InventoryItem> items = adapter.search(erp.connection("T001"), null, null, true, 0, 20);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).lowStock()).isTrue();
        assertThat(erp.decodedQueryFor("Bin")).contains("[\"item_code\",\"in\",[\"APPLE-80\"]]");
    }

    @Test
    @DisplayName("低库存筛选剔除有预警配置但当前并不低的商品")
    void lowStockFilterDropsHealthyItems() {
        erp.onList("Item Reorder", """
                {"data": [{"parent": "APPLE-80", "warehouse": "主仓库 - T", "warehouse_reorder_level": 50.0}]}
                """);
        erp.onList("Item", """
                {"data": [{"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果", "stock_uom": "箱"}]}
                """);
        erp.onList("Bin", """
                {"data": [{"item_code": "APPLE-80", "warehouse": "主仓库 - T",
                  "actual_qty": 450.0, "stock_uom": "箱"}]}
                """);
        erp.onList("Item Variant Attribute", "{\"data\": []}");

        List<InventoryItem> items = adapter.search(erp.connection("T001"), null, null, true, 0, 20);

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("指定仓库时下发 warehouse 过滤条件")
    void filtersByWarehouse() {
        erp.onList("Bin", "{\"data\": []}");

        adapter.search(erp.connection("T001"), null, "主仓库 - T", false, 0, 20);

        assertThat(erp.decodedQueryFor("Bin")).contains("[\"warehouse\",\"=\",\"主仓库 - T\"]");
    }

    @Test
    @DisplayName("按规格搜索库存：先在变体属性中反查商品编码")
    void searchesInventoryBySpec() {
        erp.onList("Item", "{\"data\": []}");
        erp.onList("Item Variant Attribute", """
                {"data": [{"parent": "APPLE-80", "attribute": "果径", "attribute_value": "80mm", "idx": 1}]}
                """);
        erp.onList("Bin", "{\"data\": []}");

        adapter.search(erp.connection("T001"), "80mm", null, false, 0, 20);

        assertThat(erp.decodedQueryFor("Bin")).contains("[\"item_code\",\"in\",[\"APPLE-80\"]]");
    }

    @Test
    @DisplayName("关键字没有命中任何商品时直接返回空，不再查询 Bin")
    void skipsBinQueryWhenNoCandidates() {
        erp.onList("Item", "{\"data\": []}");
        erp.onList("Item Variant Attribute", "{\"data\": []}");

        List<InventoryItem> items = adapter.search(erp.connection("T001"), "不存在的商品", null, false, 0, 20);

        assertThat(items).isEmpty();
        assertThat(erp.firstRequestFor("Bin")).isEmpty();
    }

    @Test
    @DisplayName("ERP 缺字段时不抛异常：Item 查不到也能返回库存数量")
    void toleratesMissingItem() {
        erp.onList("Bin", """
                {"data": [{"item_code": "GHOST-1", "warehouse": "主仓库 - T", "actual_qty": 12.5}]}
                """);
        erp.onList("Item", "{\"data\": []}");
        erp.onList("Item Variant Attribute", "{\"data\": []}");
        erp.onList("Item Reorder", "{\"data\": []}");

        InventoryItem item = adapter.search(erp.connection("T001"), null, null, false, 0, 20).get(0);

        assertThat(item.itemCode()).isEqualTo("GHOST-1");
        assertThat(item.productName()).isEqualTo("GHOST-1");
        // 小数数量必须保留，农批存在 12.5 斤这类数量
        assertThat(item.quantity()).isEqualByComparingTo("12.5");
        assertThat(item.stockUom()).isNull();
        assertThat(item.spec()).isNull();
        assertThat(item.lowStock()).isNull();
    }
}
