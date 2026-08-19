package com.nongpi.assistant.inventory.service;

import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.erp.adapter.InventoryErpAdapter;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.connection.ErpConnectionProvider;
import com.nongpi.assistant.inventory.domain.InventoryItem;
import com.nongpi.assistant.tenant.TenantContext;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryErpAdapter inventoryErpAdapter;
    private final ErpConnectionProvider erpConnectionProvider;

    public InventoryService(InventoryErpAdapter inventoryErpAdapter,
                            ErpConnectionProvider erpConnectionProvider) {
        this.inventoryErpAdapter = inventoryErpAdapter;
        this.erpConnectionProvider = erpConnectionProvider;
    }

    public PageResponse<InventoryItem> search(String keyword,
                                             boolean lowStockOnly,
                                             String warehouseId,
                                             PageRequestParams page) {
        TenantContext tenant = TenantContextHolder.require();
        ErpConnection connection = erpConnectionProvider.resolve(tenant);

        List<InventoryItem> fetched = inventoryErpAdapter.search(
                connection, keyword, warehouseId, lowStockOnly, page.offset(), page.pageSize() + 1);
        boolean hasMore = fetched.size() > page.pageSize();
        List<InventoryItem> content = hasMore ? fetched.subList(0, page.pageSize()) : fetched;

        return PageResponse.of(content, page.page(), page.pageSize(), hasMore);
    }
}
