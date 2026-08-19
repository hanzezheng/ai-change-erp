package com.nongpi.assistant.erp.connection.web;

import com.nongpi.assistant.erp.connection.ErpConnectionCommandService;
import com.nongpi.assistant.erp.connection.ErpConnectionStatus;
import com.nongpi.assistant.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/erp-connection")
public class ErpConnectionController {

    private final ErpConnectionCommandService erpConnectionCommandService;

    public ErpConnectionController(ErpConnectionCommandService erpConnectionCommandService) {
        this.erpConnectionCommandService = erpConnectionCommandService;
    }

    @GetMapping
    @PreAuthorize("@roles.atLeast('ADMIN')")
    public ErpConnectionCommandService.ErpConnectionView get() {
        return erpConnectionCommandService.getForTenant(SecurityUtils.requireUser().tenantId());
    }

    @PutMapping
    @PreAuthorize("@roles.atLeast('ADMIN')")
    public ErpConnectionCommandService.ErpConnectionView upsert(@Valid @RequestBody UpsertErpConnectionRequest request) {
        return erpConnectionCommandService.upsert(SecurityUtils.requireUser(),
                new ErpConnectionCommandService.UpsertCommand(
                        request.baseUrl(),
                        request.siteName(),
                        request.apiKey(),
                        request.apiSecret(),
                        request.sellingPriceList(),
                        request.defaultWarehouse(),
                        request.status(),
                        request.connectTimeoutMs(),
                        request.readTimeoutMs()
                ));
    }

    public record UpsertErpConnectionRequest(
            @NotBlank String baseUrl,
            String siteName,
            String apiKey,
            String apiSecret,
            String sellingPriceList,
            String defaultWarehouse,
            ErpConnectionStatus status,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }
}
