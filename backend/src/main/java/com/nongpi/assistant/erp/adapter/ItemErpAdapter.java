package com.nongpi.assistant.erp.adapter;

import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.product.domain.ProductVariant;

import java.util.List;
import java.util.Optional;

/**
 * 商品主数据的 ERP 出口。
 *
 * <p>返回的每个 {@link ProductVariant} 都已带齐 allowedUoms 与参考价格，
 * 因为「选了商品才发现没有合法单位」在订单流程里是不可用的状态。
 */
public interface ItemErpAdapter {

    List<ProductVariant> search(ErpConnection connection, String keyword, int offset, int limit);

    Optional<ProductVariant> findByItemCode(ErpConnection connection, String itemCode);
}
