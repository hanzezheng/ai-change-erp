package com.nongpi.assistant.erp.adapter.frappe;

import com.nongpi.assistant.erp.adapter.ItemErpAdapter;
import com.nongpi.assistant.erp.client.ErpFilter;
import com.nongpi.assistant.erp.client.ErpQuery;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpItemPrice;
import com.nongpi.assistant.erp.dto.ErpUomConversion;
import com.nongpi.assistant.erp.mapper.ProductErpMapper;
import com.nongpi.assistant.erp.mapper.ErpValues;
import com.nongpi.assistant.product.domain.ProductVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class FrappeItemErpAdapter implements ItemErpAdapter {

    private static final Logger log = LoggerFactory.getLogger(FrappeItemErpAdapter.class);

    private static final String[] ITEM_FIELDS = {
            "name", "item_code", "item_name", "description", "stock_uom", "sales_uom",
            "variant_of", "item_group", "has_variants", "disabled", "is_sales_item", "safety_stock"
    };

    /** 按规格反查商品时，最多带回多少个候选 item_code 进入主查询。 */
    private static final int SPEC_MATCH_LIMIT = 50;

    private final ErpRestClient erpRestClient;
    private final ProductErpMapper mapper;

    public FrappeItemErpAdapter(ErpRestClient erpRestClient, ProductErpMapper mapper) {
        this.erpRestClient = erpRestClient;
        this.mapper = mapper;
    }

    @Override
    public List<ProductVariant> search(ErpConnection connection, String keyword, int offset, int limit) {
        List<ErpItem> items = searchItems(connection, keyword, offset, limit);
        return assemble(connection, items);
    }

    @Override
    public Optional<ProductVariant> findByItemCode(ErpConnection connection, String itemCode) {
        return erpRestClient.getDoc(connection, ErpItem.DOCTYPE, itemCode, ErpItem.class)
                .map(item -> assemble(connection, List.of(item)))
                .filter(variants -> !variants.isEmpty())
                .map(variants -> variants.get(0));
    }

    @Override
    public Optional<ProductVariant> findOrderableByItemCode(ErpConnection connection, String itemCode) {
        return erpRestClient.getDoc(connection, ErpItem.DOCTYPE, itemCode, ErpItem.class)
                .filter(this::isOrderable)
                .map(item -> assemble(connection, List.of(item)))
                .filter(variants -> !variants.isEmpty())
                .map(variants -> variants.get(0));
    }

    private boolean isOrderable(ErpItem item) {
        if (ErpValues.isTruthy(item.disabled()) || ErpValues.isTruthy(item.hasVariants())) {
            return false;
        }
        return item.isSalesItem() == null || ErpValues.isTruthy(item.isSalesItem());
    }

    private List<ErpItem> searchItems(ErpConnection connection, String keyword, int offset, int limit) {
        ErpQuery query = ErpQuery.create()
                .fields(ITEM_FIELDS)
                .filter(ErpFilter.eq("disabled", 0))
                .filter(ErpFilter.eq("is_sales_item", 1))
                // 模板商品（has_variants=1）不能直接进订单，订单行必须落到具体变体。
                .filter(ErpFilter.eq("has_variants", 0))
                .orderBy("item_name asc")
                .limit(offset, limit);

        String pattern = FrappeSearch.likePattern(keyword);
        if (pattern != null) {
            query.orFilter(ErpFilter.like("item_name", pattern))
                    .orFilter(ErpFilter.like("name", pattern));
            List<String> specMatches = findItemCodesBySpec(connection, pattern);
            if (!specMatches.isEmpty()) {
                query.orFilter(ErpFilter.in("name", specMatches));
            }
        }
        return erpRestClient.list(connection, ErpItem.DOCTYPE, query, ErpItem.class);
    }

    /**
     * 支持按规格搜索（docs/05_UI_SPEC.md #14）。规格存在 Item Variant Attribute 子表里，
     * 无法用 Item 上的字段匹配，所以先反查出候选 item_code。
     */
    private List<String> findItemCodesBySpec(ErpConnection connection, String pattern) {
        ErpQuery query = ErpQuery.create()
                .fields("parent")
                .filter(ErpFilter.eq("parenttype", ErpItem.DOCTYPE))
                .filter(ErpFilter.like("attribute_value", pattern))
                .parent(ErpItem.DOCTYPE)
                .limit(0, SPEC_MATCH_LIMIT);
        return erpRestClient.list(connection, ErpItemAttribute.DOCTYPE, query, ErpItemAttribute.class)
                .stream()
                .map(ErpItemAttribute::parent)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 批量补齐子表与价格，再逐个映射。子表通过 {@code parent=Item} 一次查回，
     * 避免逐个商品拉完整文档造成的 N+1。
     */
    private List<ProductVariant> assemble(ErpConnection connection, List<ErpItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<String> itemCodes = items.stream()
                .map(ErpItem::resolvedItemCode)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (itemCodes.isEmpty()) {
            return List.of();
        }

        Map<String, List<ErpUomConversion>> uomsByItem = fetchUomConversions(connection, itemCodes);
        Map<String, List<ErpItemAttribute>> attributesByItem = fetchAttributes(connection, itemCodes);
        Map<String, List<ErpItemPrice>> pricesByItem = fetchPrices(connection, itemCodes);

        return items.stream()
                .map(item -> {
                    String code = item.resolvedItemCode();
                    return mapper.toProductVariant(
                            item,
                            uomsByItem.getOrDefault(code, List.of()),
                            attributesByItem.getOrDefault(code, List.of()),
                            pricesByItem.getOrDefault(code, List.of())
                    );
                })
                .toList();
    }

    private Map<String, List<ErpUomConversion>> fetchUomConversions(ErpConnection connection, List<String> itemCodes) {
        ErpQuery query = ErpQuery.create()
                .fields("parent", "uom", "conversion_factor", "idx")
                .filter(ErpFilter.eq("parenttype", ErpItem.DOCTYPE))
                .filter(ErpFilter.in("parent", itemCodes))
                .parent(ErpItem.DOCTYPE)
                .unlimited();
        return erpRestClient.list(connection, ErpUomConversion.DOCTYPE, query, ErpUomConversion.class)
                .stream()
                .filter(row -> row.parent() != null)
                .collect(Collectors.groupingBy(ErpUomConversion::parent));
    }

    private Map<String, List<ErpItemAttribute>> fetchAttributes(ErpConnection connection, List<String> itemCodes) {
        ErpQuery query = ErpQuery.create()
                .fields("parent", "attribute", "attribute_value", "idx")
                .filter(ErpFilter.eq("parenttype", ErpItem.DOCTYPE))
                .filter(ErpFilter.in("parent", itemCodes))
                .parent(ErpItem.DOCTYPE)
                .unlimited();
        return erpRestClient.list(connection, ErpItemAttribute.DOCTYPE, query, ErpItemAttribute.class)
                .stream()
                .filter(row -> row.parent() != null)
                .collect(Collectors.groupingBy(ErpItemAttribute::parent));
    }

    /**
     * 参考价格来自 ERPNext Item Price，系统不建第二套价格表。
     *
     * <p>没有配置销售价格表时不做猜测：宁可 referencePrice 为空，
     * 也不能把某个不确定的价格表当成该租户的售价来源。
     */
    private Map<String, List<ErpItemPrice>> fetchPrices(ErpConnection connection, List<String> itemCodes) {
        String priceList = connection.sellingPriceList();
        if (priceList == null || priceList.isBlank()) {
            log.warn("租户 {} 未配置 selling-price-list，商品参考价格将为空", connection.tenantId());
            return Map.of();
        }
        ErpQuery query = ErpQuery.create()
                .fields("name", "item_code", "price_list", "price_list_rate", "currency", "uom")
                .filter(ErpFilter.eq("selling", 1))
                .filter(ErpFilter.eq("price_list", priceList))
                .filter(ErpFilter.in("item_code", itemCodes))
                .orderBy("valid_from desc")
                .unlimited();
        return erpRestClient.list(connection, ErpItemPrice.DOCTYPE, query, ErpItemPrice.class)
                .stream()
                .filter(price -> price.itemCode() != null)
                .collect(Collectors.groupingBy(ErpItemPrice::itemCode));
    }
}
