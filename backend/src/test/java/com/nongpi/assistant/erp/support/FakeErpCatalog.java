package com.nongpi.assistant.erp.support;

import com.nongpi.assistant.erp.dto.ErpCustomer;
import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpItemPrice;
import com.nongpi.assistant.erp.dto.ErpUomConversion;

public final class FakeErpCatalog {

    private FakeErpCatalog() {
    }

    public static void goldenPath(FakeErpNext erp) {
        erp.onDoc(ErpCustomer.DOCTYPE, "韩兆亮", """
                {"data": {"name": "韩兆亮", "customer_name": "韩兆亮", "disabled": 0}}
                """);
        erp.onDoc(ErpItem.DOCTYPE, "APPLE-80", item("APPLE-80", "苹果80果", "APPLE", 0, 1));
        erp.onDoc(ErpItem.DOCTYPE, "APPLE-70", item("APPLE-70", "苹果70果", "APPLE", 0, 1));
        erp.onDoc(ErpItem.DOCTYPE, "BANANA-FEN", item("BANANA-FEN", "香蕉粉蕉", null, 0, 1));
        erp.onDoc(ErpItem.DOCTYPE, "APPLE", item("APPLE", "苹果", null, 1, 1));
        erp.onList(ErpItem.DOCTYPE, """
                {"data": [
                  {"name": "APPLE-80", "item_code": "APPLE-80", "item_name": "苹果80果",
                   "stock_uom": "箱", "sales_uom": "箱", "variant_of": "APPLE",
                   "has_variants": 0, "disabled": 0, "is_sales_item": 1},
                  {"name": "APPLE-70", "item_code": "APPLE-70", "item_name": "苹果70果",
                   "stock_uom": "箱", "sales_uom": "箱", "variant_of": "APPLE",
                   "has_variants": 0, "disabled": 0, "is_sales_item": 1},
                  {"name": "BANANA-FEN", "item_code": "BANANA-FEN", "item_name": "香蕉粉蕉",
                   "stock_uom": "件", "sales_uom": "件",
                   "has_variants": 0, "disabled": 0, "is_sales_item": 1}
                ]}
                """);
        erp.onList(ErpUomConversion.DOCTYPE, """
                {"data": [
                  {"parent": "APPLE-80", "uom": "箱", "conversion_factor": 1.0, "idx": 1},
                  {"parent": "APPLE-80", "uom": "斤", "conversion_factor": 20.0, "idx": 2},
                  {"parent": "APPLE-70", "uom": "箱", "conversion_factor": 1.0, "idx": 1},
                  {"parent": "BANANA-FEN", "uom": "件", "conversion_factor": 1.0, "idx": 1}
                ]}
                """);
        erp.onList(ErpItemAttribute.DOCTYPE, """
                {"data": [
                  {"parent": "APPLE-80", "attribute": "规格", "attribute_value": "80果", "idx": 1},
                  {"parent": "APPLE-70", "attribute": "规格", "attribute_value": "70果", "idx": 1}
                ]}
                """);
        erp.onList(ErpItemPrice.DOCTYPE, """
                {"data": [
                  {"item_code": "APPLE-80", "price_list": "Standard Selling", "price_list_rate": 68, "uom": "箱", "currency": "CNY"},
                  {"item_code": "APPLE-80", "price_list": "Standard Selling", "price_list_rate": 3.8, "uom": "斤", "currency": "CNY"},
                  {"item_code": "BANANA-FEN", "price_list": "Standard Selling", "price_list_rate": 32, "uom": "件", "currency": "CNY"}
                ]}
                """);
    }

    private static String item(String code, String name, String variantOf, int hasVariants, int sales) {
        String variant = variantOf == null ? "null" : "\"" + variantOf + "\"";
        return """
                {"data": {"name": "%s", "item_code": "%s", "item_name": "%s",
                  "stock_uom": "箱", "sales_uom": "箱", "variant_of": %s,
                  "has_variants": %s, "disabled": 0, "is_sales_item": %s}}
                """.formatted(code, code, name, variant, hasVariants, sales);
    }
}
