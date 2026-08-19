package com.nongpi.assistant.erp.mapper;

import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpItemPrice;
import com.nongpi.assistant.erp.dto.ErpUomConversion;
import com.nongpi.assistant.product.domain.AllowedUom;
import com.nongpi.assistant.product.domain.ProductVariant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERPNext Item（含 uoms / attributes 子表与 Item Price）→ App ProductVariant 映射。
 *
 * <p>本类是「商品身份、合法单位、参考价格」三件事的唯一映射点，
 * 不依赖 HTTP，可以直接用 ERP DTO 做单元测试。
 *
 * <p>这里产出的 referencePrice 只是展示与默认参考价，不是成交价，
 * 边界见 {@link AllowedUom}。
 */
@Component
public class ProductErpMapper {

    public ProductVariant toProductVariant(ErpItem item,
                                           List<ErpUomConversion> uomConversions,
                                           List<ErpItemAttribute> attributes,
                                           List<ErpItemPrice> prices) {
        String itemCode = ErpValues.trimToNull(item.resolvedItemCode());
        String stockUom = ErpValues.trimToNull(item.stockUom());
        String variantOf = ErpValues.trimToNull(item.variantOf());

        PriceIndex priceIndex = PriceIndex.of(prices);
        List<AllowedUom> allowedUoms = buildAllowedUoms(uomConversions, stockUom, priceIndex);
        String defaultUom = resolveDefaultUom(item, stockUom, allowedUoms);
        AllowedUom defaultUomEntry = findUom(allowedUoms, defaultUom);

        return new ProductVariant(
                // productId 只是商品族分组：变体取模板，非变体取自身编码
                variantOf != null ? variantOf : itemCode,
                itemCode,
                ErpValues.trimToNull(item.itemName()) != null ? item.itemName().trim() :                 itemCode,
                ErpSpec.fromAttributes(attributes),
                List.of(),
                defaultUom,
                allowedUoms,
                defaultUomEntry == null ? null : defaultUomEntry.referencePrice(),
                defaultUomEntry == null || defaultUomEntry.referencePrice() == null ? null : defaultUom,
                defaultUomEntry == null ? null : defaultUomEntry.currency(),
                null
        );
    }

    private List<AllowedUom> buildAllowedUoms(List<ErpUomConversion> uomConversions,
                                              String stockUom,
                                              PriceIndex priceIndex) {
        Map<String, AllowedUom> byUom = new LinkedHashMap<>();
        if (uomConversions != null) {
            uomConversions.stream()
                    .sorted(Comparator.comparing(row -> row.idx() == null ? Integer.MAX_VALUE : row.idx()))
                    .forEach(row -> {
                        String uom = ErpValues.trimToNull(row.uom());
                        if (uom == null || byUom.containsKey(uom)) {
                            return;
                        }
                        byUom.put(uom, newAllowedUom(uom, row.conversionFactor(), stockUom, priceIndex));
                    });
        }
        // ERPNext 正常会把 stock_uom 写进 uoms 子表。子表缺失时仍必须保证至少有
        // 一个合法单位，否则商品无法进入订单；此时按库存单位补一条，换算率为 1。
        if (byUom.isEmpty() && stockUom != null) {
            byUom.put(stockUom, newAllowedUom(stockUom, BigDecimal.ONE, stockUom, priceIndex));
        }
        return List.copyOf(new ArrayList<>(byUom.values()));
    }

    private AllowedUom newAllowedUom(String uom, BigDecimal conversionFactor, String stockUom, PriceIndex priceIndex) {
        ErpItemPrice price = priceIndex.forUom(uom, stockUom);
        return new AllowedUom(
                uom,
                conversionFactor,
                price == null ? null : price.priceListRate(),
                price == null ? null : ErpValues.trimToNull(price.currency())
        );
    }

    /**
     * ERPNext 的 sales_uom 是销售默认单位，未配置时用库存单位。
     * 若该单位不在合法单位列表里（ERP 数据异常），回落到第一个合法单位，
     * 避免把一个不允许使用的单位当默认值发给客户端。
     */
    private String resolveDefaultUom(ErpItem item, String stockUom, List<AllowedUom> allowedUoms) {
        String candidate = ErpValues.trimToNull(item.salesUom());
        if (candidate == null) {
            candidate = stockUom;
        }
        if (candidate != null && findUom(allowedUoms, candidate) != null) {
            return candidate;
        }
        if (stockUom != null && findUom(allowedUoms, stockUom) != null) {
            return stockUom;
        }
        return allowedUoms.isEmpty() ? null : allowedUoms.get(0).uom();
    }

    private AllowedUom findUom(List<AllowedUom> allowedUoms, String uom) {
        if (uom == null) {
            return null;
        }
        return allowedUoms.stream().filter(entry -> uom.equals(entry.uom())).findFirst().orElse(null);
    }

    /**
     * Item Price 按 UOM 建索引。
     *
     * <p>ERPNext 允许 Item Price 不填 UOM。这种价格只作为库存单位的兜底，
     * 不套用到其他单位 —— 把箱价当斤价用是 AGENTS.md #32 明确禁止的。
     */
    private record PriceIndex(Map<String, ErpItemPrice> byUom, ErpItemPrice withoutUom) {

        static PriceIndex of(List<ErpItemPrice> prices) {
            Map<String, ErpItemPrice> byUom = new LinkedHashMap<>();
            ErpItemPrice withoutUom = null;
            if (prices != null) {
                for (ErpItemPrice price : prices) {
                    if (price.priceListRate() == null) {
                        continue;
                    }
                    String uom = ErpValues.trimToNull(price.uom());
                    if (uom == null) {
                        if (withoutUom == null) {
                            withoutUom = price;
                        }
                    } else {
                        byUom.putIfAbsent(uom, price);
                    }
                }
            }
            return new PriceIndex(byUom, withoutUom);
        }

        ErpItemPrice forUom(String uom, String stockUom) {
            ErpItemPrice exact = byUom.get(uom);
            if (exact != null) {
                return exact;
            }
            return uom != null && uom.equals(stockUom) ? withoutUom : null;
        }
    }
}
