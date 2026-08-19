package com.nongpi.assistant.inventory.web;

import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.inventory.domain.InventoryItem;
import com.nongpi.assistant.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public PageResponse<InventoryItem> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false, defaultValue = "false") boolean lowStock,
                                            @RequestParam(required = false) String warehouseId,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer pageSize) {
        return inventoryService.search(q, lowStock, warehouseId, PageRequestParams.of(page, pageSize));
    }
}
