package com.nongpi.assistant.erp.mapper;

import com.nongpi.assistant.erp.dto.ErpBin;
import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpItemReorder;
import com.nongpi.assistant.inventory.domain.InventoryItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * ERPNext Bin（+ Item / Item Variant Attribute / Item Reorder）→ App InventoryItem 映射
 * （docs/06_API_DATA_DESIGN.md #74）。
 */
@Component
public class InventoryErpMapper {

    /**
     * @param item          Bin 对应的 Item，ERP 数据不一致时可能为 null
     * @param attributes    该商品的变体属性，用于推导规格，可能为空
     * @param reorderLevels 该商品的补货预警配置，可能为空
     */
    public InventoryItem toInventoryItem(ErpBin bin,
                                         ErpItem item,
                                         List<ErpItemAttribute> attributes,
                                         List<ErpItemReorder> reorderLevels) {
        String itemCode = ErpValues.trimToNull(bin.itemCode());
        String warehouse = ErpValues.trimToNull(bin.warehouse());
        BigDecimal quantity = bin.actualQty();

        String stockUom = ErpValues.trimToNull(bin.stockUom());
        if (stockUom == null && item != null) {
            stockUom = ErpValues.trimToNull(item.stockUom());
        }

        String variantOf = item == null ? null : ErpValues.trimToNull(item.variantOf());
        String itemName = item == null ? null : ErpValues.trimToNull(item.itemName());
        BigDecimal alertQty = resolveAlertQty(warehouse, item, reorderLevels);

        return new InventoryItem(
                variantOf != null ? variantOf : itemCode,
                itemCode,
                itemCode,
                itemName != null ? itemName : itemCode,
                ErpSpec.fromAttributes(attributes),
                quantity,
                stockUom,
                warehouse,
                alertQty,
                resolveLowStock(quantity, alertQty)
        );
    }

    /**
     * 优先取该仓库的补货预警线；没有仓库级配置时退到 Item 上的安全库存。
     * 两者都没有就返回 null —— 不编一个预警值出来。
     */
    private BigDecimal resolveAlertQty(String warehouse, ErpItem item, List<ErpItemReorder> reorderLevels) {
        if (reorderLevels != null && warehouse != null) {
            BigDecimal warehouseLevel = reorderLevels.stream()
                    .filter(level -> warehouse.equals(ErpValues.trimToNull(level.warehouse())))
                    .map(ErpItemReorder::warehouseReorderLevel)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (warehouseLevel != null) {
                return warehouseLevel;
            }
        }
        if (item != null && item.safetyStock() != null && item.safetyStock().signum() > 0) {
            return item.safetyStock();
        }
        return null;
    }

    /**
     * 没有预警配置就返回 null，表示「无法判断」，而不是默认 false。
     */
    private Boolean resolveLowStock(BigDecimal quantity, BigDecimal alertQty) {
        if (alertQty == null || quantity == null) {
            return null;
        }
        return quantity.compareTo(alertQty) <= 0;
    }
}
