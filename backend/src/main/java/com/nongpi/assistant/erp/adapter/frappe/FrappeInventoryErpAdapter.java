package com.nongpi.assistant.erp.adapter.frappe;

import com.nongpi.assistant.erp.adapter.InventoryErpAdapter;
import com.nongpi.assistant.erp.client.ErpFilter;
import com.nongpi.assistant.erp.client.ErpQuery;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.dto.ErpBin;
import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpItemReorder;
import com.nongpi.assistant.erp.mapper.InventoryErpMapper;
import com.nongpi.assistant.inventory.domain.InventoryItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FrappeInventoryErpAdapter implements InventoryErpAdapter {

    /** 关键字或低库存筛选时，最多带回多少个候选 item_code 进入 Bin 查询。 */
    private static final int CANDIDATE_LIMIT = 500;

    private final ErpRestClient erpRestClient;
    private final InventoryErpMapper mapper;

    public FrappeInventoryErpAdapter(ErpRestClient erpRestClient, InventoryErpMapper mapper) {
        this.erpRestClient = erpRestClient;
        this.mapper = mapper;
    }

    @Override
    public List<InventoryItem> search(ErpConnection connection,
                                      String keyword,
                                      String warehouseId,
                                      boolean lowStockOnly,
                                      int offset,
                                      int limit) {
        Optional<Set<String>> candidates = resolveCandidateItemCodes(connection, keyword, lowStockOnly);
        if (candidates.isPresent() && candidates.get().isEmpty()) {
            return List.of();
        }

        List<ErpBin> bins = fetchBins(connection, candidates.orElse(null), warehouseId, offset, limit);
        if (bins.isEmpty()) {
            return List.of();
        }

        List<String> itemCodes = bins.stream()
                .map(ErpBin::itemCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, ErpItem> itemsByCode = fetchItems(connection, itemCodes);
        Map<String, List<ErpItemAttribute>> attributesByItem = fetchAttributes(connection, itemCodes);
        Map<String, List<ErpItemReorder>> reordersByItem = fetchReorders(connection, itemCodes);

        List<InventoryItem> mapped = bins.stream()
                .map(bin -> mapper.toInventoryItem(
                        bin,
                        itemsByCode.get(bin.itemCode()),
                        attributesByItem.getOrDefault(bin.itemCode(), List.of()),
                        reordersByItem.getOrDefault(bin.itemCode(), List.of())))
                .toList();

        if (!lowStockOnly) {
            return mapped;
        }
        // 候选集已限定为「配置了预警线的商品」，这里再按实际数量与预警线比较，
        // 剔除有预警配置但当前并不低的行。
        return mapped.stream().filter(item -> Boolean.TRUE.equals(item.lowStock())).toList();
    }

    /**
     * @return {@link Optional#empty()} 表示不限定商品；返回空集合表示没有任何候选，调用方应直接返回空结果
     */
    private Optional<Set<String>> resolveCandidateItemCodes(ErpConnection connection,
                                                            String keyword,
                                                            boolean lowStockOnly) {
        Set<String> candidates = null;
        String pattern = FrappeSearch.likePattern(keyword);
        if (pattern != null) {
            candidates = new LinkedHashSet<>(findItemCodesByKeyword(connection, pattern));
        }
        if (lowStockOnly) {
            Set<String> alertConfigured = findItemCodesWithStockAlert(connection);
            if (candidates == null) {
                candidates = alertConfigured;
            } else {
                candidates.retainAll(alertConfigured);
            }
        }
        return Optional.ofNullable(candidates);
    }

    /**
     * 库存页支持按商品名称、编码和规格搜索（docs/05_UI_SPEC.md #37）。
     * Bin 上只有 item_code，名称和规格都要先在 Item 侧反查。
     */
    private List<String> findItemCodesByKeyword(ErpConnection connection, String pattern) {
        ErpQuery itemQuery = ErpQuery.create()
                .fields("name")
                .filter(ErpFilter.eq("disabled", 0))
                .orFilter(ErpFilter.like("item_name", pattern))
                .orFilter(ErpFilter.like("name", pattern))
                .limit(0, CANDIDATE_LIMIT);
        List<String> byName = erpRestClient.list(connection, ErpItem.DOCTYPE, itemQuery, ErpItem.class)
                .stream()
                .map(ErpItem::name)
                .filter(Objects::nonNull)
                .toList();

        ErpQuery specQuery = ErpQuery.create()
                .fields("parent")
                .filter(ErpFilter.eq("parenttype", ErpItem.DOCTYPE))
                .filter(ErpFilter.like("attribute_value", pattern))
                .parent(ErpItem.DOCTYPE)
                .limit(0, CANDIDATE_LIMIT);
        List<String> bySpec = erpRestClient.list(connection, ErpItemAttribute.DOCTYPE, specQuery, ErpItemAttribute.class)
                .stream()
                .map(ErpItemAttribute::parent)
                .filter(Objects::nonNull)
                .toList();

        Set<String> merged = new LinkedHashSet<>(byName);
        merged.addAll(bySpec);
        return List.copyOf(merged);
    }

    /**
     * 只有配置了补货预警线或安全库存的商品才可能被判定为低库存
     * （AGENTS.md #83：没有明确预警配置就不显示低库存）。
     */
    private Set<String> findItemCodesWithStockAlert(ErpConnection connection) {
        ErpQuery reorderQuery = ErpQuery.create()
                .fields("parent", "warehouse", "warehouse_reorder_level")
                .filter(ErpFilter.eq("parenttype", ErpItem.DOCTYPE))
                .filter(ErpFilter.greaterThan("warehouse_reorder_level", 0))
                .parent(ErpItem.DOCTYPE)
                .limit(0, CANDIDATE_LIMIT);
        Set<String> codes = erpRestClient.list(connection, ErpItemReorder.DOCTYPE, reorderQuery, ErpItemReorder.class)
                .stream()
                .map(ErpItemReorder::parent)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ErpQuery safetyStockQuery = ErpQuery.create()
                .fields("name")
                .filter(ErpFilter.eq("disabled", 0))
                .filter(ErpFilter.greaterThan("safety_stock", 0))
                .limit(0, CANDIDATE_LIMIT);
        erpRestClient.list(connection, ErpItem.DOCTYPE, safetyStockQuery, ErpItem.class)
                .stream()
                .map(ErpItem::name)
                .filter(Objects::nonNull)
                .forEach(codes::add);

        return codes;
    }

    private List<ErpBin> fetchBins(ErpConnection connection,
                                   Set<String> candidateItemCodes,
                                   String warehouseId,
                                   int offset,
                                   int limit) {
        ErpQuery query = ErpQuery.create()
                .fields("item_code", "warehouse", "actual_qty", "stock_uom")
                .orderBy("item_code asc")
                .limit(offset, limit);
        if (candidateItemCodes != null) {
            query.filter(ErpFilter.in("item_code", List.copyOf(candidateItemCodes)));
        }
        String warehouse = warehouseId == null ? null : warehouseId.trim();
        if (warehouse != null && !warehouse.isEmpty()) {
            query.filter(ErpFilter.eq("warehouse", warehouse));
        }
        return erpRestClient.list(connection, ErpBin.DOCTYPE, query, ErpBin.class);
    }

    private Map<String, ErpItem> fetchItems(ErpConnection connection, List<String> itemCodes) {
        ErpQuery query = ErpQuery.create()
                .fields("name", "item_code", "item_name", "stock_uom", "variant_of", "safety_stock")
                .filter(ErpFilter.in("name", itemCodes))
                .unlimited();
        return erpRestClient.list(connection, ErpItem.DOCTYPE, query, ErpItem.class)
                .stream()
                .filter(item -> item.resolvedItemCode() != null)
                .collect(Collectors.toMap(ErpItem::resolvedItemCode, Function.identity(), (first, second) -> first));
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

    private Map<String, List<ErpItemReorder>> fetchReorders(ErpConnection connection, List<String> itemCodes) {
        ErpQuery query = ErpQuery.create()
                .fields("parent", "warehouse", "warehouse_reorder_level")
                .filter(ErpFilter.eq("parenttype", ErpItem.DOCTYPE))
                .filter(ErpFilter.in("parent", itemCodes))
                .parent(ErpItem.DOCTYPE)
                .unlimited();
        return erpRestClient.list(connection, ErpItemReorder.DOCTYPE, query, ErpItemReorder.class)
                .stream()
                .filter(row -> row.parent() != null)
                .collect(Collectors.groupingBy(ErpItemReorder::parent));
    }
}
