package com.nongpi.assistant.product.service;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.adapter.ItemErpAdapter;
import com.nongpi.assistant.erp.adapter.SalesOrderErpAdapter;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.connection.ErpConnectionProvider;
import com.nongpi.assistant.identity.ProductAliasProvider;
import com.nongpi.assistant.product.domain.AllowedUom;
import com.nongpi.assistant.product.domain.ProductSelectorResult;
import com.nongpi.assistant.product.domain.ProductVariant;
import com.nongpi.assistant.tenant.TenantContext;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    private static final int SELECTOR_RESULT_LIMIT = 30;

    private final ItemErpAdapter itemErpAdapter;
    private final SalesOrderErpAdapter salesOrderErpAdapter;
    private final ErpConnectionProvider erpConnectionProvider;
    private final ProductAliasProvider productAliasProvider;

    public ProductService(ItemErpAdapter itemErpAdapter,
                          SalesOrderErpAdapter salesOrderErpAdapter,
                          ErpConnectionProvider erpConnectionProvider,
                          ProductAliasProvider productAliasProvider) {
        this.itemErpAdapter = itemErpAdapter;
        this.salesOrderErpAdapter = salesOrderErpAdapter;
        this.erpConnectionProvider = erpConnectionProvider;
        this.productAliasProvider = productAliasProvider;
    }

    /**
     * 商品选择器。带 customerId 时，frequentItems 与 lastDealPrice 来自已提交 Sales Order。
     */
    public ProductSelectorResult selector(String keyword, String customerId) {
        TenantContext tenant = TenantContextHolder.require();
        ErpConnection connection = erpConnectionProvider.resolve(tenant);
        List<ProductVariant> results = withAliases(tenant,
                itemErpAdapter.search(connection, keyword, 0, SELECTOR_RESULT_LIMIT));
        List<ProductVariant> frequent = List.of();
        if (customerId != null && !customerId.isBlank()) {
            List<String> itemCodes = salesOrderErpAdapter.frequentItemCodes(connection, customerId, 8);
            frequent = withAliases(tenant, itemCodes.stream()
                    .map(code -> itemErpAdapter.findByItemCode(connection, code))
                    .flatMap(java.util.Optional::stream)
                    .toList());
            frequent = withLastDeal(connection, customerId, frequent);
            results = withLastDeal(connection, customerId, results);
        }
        return new ProductSelectorResult(frequent, results);
    }

    public ProductVariant getByItemCode(String itemCode) {
        TenantContext tenant = TenantContextHolder.require();
        ErpConnection connection = erpConnectionProvider.resolve(tenant);
        ProductVariant variant = itemErpAdapter.findByItemCode(connection, itemCode)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ITEM_NOT_FOUND,
                        BusinessErrorCode.ITEM_NOT_FOUND.defaultMessage(),
                        Map.of("itemCode", itemCode)));
        return withAliases(tenant, List.of(variant)).get(0);
    }

    public ProductVariant requireOrderableItem(String itemCode) {
        TenantContext tenant = TenantContextHolder.require();
        ErpConnection connection = erpConnectionProvider.resolve(tenant);
        ProductVariant variant = itemErpAdapter.findOrderableByItemCode(connection, itemCode)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ITEM_NOT_FOUND,
                        BusinessErrorCode.ITEM_NOT_FOUND.defaultMessage(),
                        Map.of("itemCode", itemCode)));
        return withAliases(tenant, List.of(variant)).get(0);
    }

    /**
     * 校验单位是否属于该商品在 ERPNext 中配置的合法单位。
     *
     * <p>这是「禁止自由输入不存在的 UOM 并提交」这条规则的服务端落点
     * （AGENTS.md #30、#69）。本轮只读链路不写单据，但订单与 AI 阶段都必须走这里，
     * 因此校验逻辑与 allowedUoms 的来源放在一起，避免将来另写一份。
     *
     * @return 该 UOM 对应的合法条目（含与之绑定的参考价）
     */
    public AllowedUom requireAllowedUom(String itemCode, String uom) {
        ProductVariant variant = requireOrderableItem(itemCode);
        if (uom == null || uom.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_UOM, "未指定单位",
                    Map.of("itemCode", itemCode, "allowedUoms", uomNames(variant)));
        }
        return variant.allowedUoms().stream()
                .filter(candidate -> uom.equals(candidate.uom()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.INVALID_UOM,
                        "该商品不支持单位「" + uom + "」",
                        Map.of("itemCode", itemCode, "uom", uom, "allowedUoms", uomNames(variant))));
    }

    private List<ProductVariant> withLastDeal(ErpConnection connection, String customerId, List<ProductVariant> variants) {
        List<ProductVariant> enriched = new ArrayList<>(variants.size());
        for (ProductVariant variant : variants) {
            var last = salesOrderErpAdapter.findLastDealPrice(connection, customerId, variant.itemCode(), variant.defaultUom());
            enriched.add(last.map(price -> variant.withLastDealPrice(price.price())).orElse(variant));
        }
        return enriched;
    }

    private List<String> uomNames(ProductVariant variant) {
        return variant.allowedUoms().stream().map(AllowedUom::uom).toList();
    }

    private List<ProductVariant> withAliases(TenantContext tenant, List<ProductVariant> variants) {
        if (variants.isEmpty()) {
            return List.of();
        }
        Set<String> itemCodes = new LinkedHashSet<>();
        variants.forEach(variant -> itemCodes.add(variant.itemCode()));
        Map<String, List<String>> aliases = productAliasProvider.findAliases(tenant, itemCodes);

        List<ProductVariant> enriched = new ArrayList<>(variants.size());
        for (ProductVariant variant : variants) {
            enriched.add(variant.withAliases(aliases.getOrDefault(variant.itemCode(), List.of())));
        }
        return enriched;
    }
}
