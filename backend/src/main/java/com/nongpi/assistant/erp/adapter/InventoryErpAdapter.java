package com.nongpi.assistant.erp.adapter;

import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.inventory.domain.InventoryItem;

import java.util.List;

/**
 * 库存查询的 ERP 出口。
 *
 * <p>本轮只覆盖商品选择与库存页所需的最小查询，不做出入库等库存流程。
 */
public interface InventoryErpAdapter {

    /**
     * @param keyword      商品名称 / 编码 / 规格
     * @param warehouseId  为空表示不限仓库
     * @param lowStockOnly 只看低于预警线的商品。库存低不低只能对配置了预警线的商品判断，
     *                     因此该筛选会先把候选限定在有预警配置的商品上。
     */
    List<InventoryItem> search(ErpConnection connection,
                               String keyword,
                               String warehouseId,
                               boolean lowStockOnly,
                               int offset,
                               int limit);
}
