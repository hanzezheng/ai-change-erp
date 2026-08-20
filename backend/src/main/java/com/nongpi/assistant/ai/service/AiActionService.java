package com.nongpi.assistant.ai.service;

import com.nongpi.assistant.ai.client.AiServiceClient;
import com.nongpi.assistant.ai.dto.AiActionRequest;
import com.nongpi.assistant.ai.dto.AiActionResponse;
import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.customer.domain.CustomerSummary;
import com.nongpi.assistant.customer.service.CustomerService;
import com.nongpi.assistant.product.domain.AllowedUom;
import com.nongpi.assistant.product.domain.ProductVariant;
import com.nongpi.assistant.product.service.ProductService;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring 负责鉴权、租户、候选集装配与是否允许执行；Python 只负责理解。
 */
@Service
public class AiActionService {

    private final AiServiceClient aiServiceClient;
    private final CustomerService customerService;
    private final ProductService productService;

    public AiActionService(AiServiceClient aiServiceClient,
                           CustomerService customerService,
                           ProductService productService) {
        this.aiServiceClient = aiServiceClient;
        this.customerService = customerService;
        this.productService = productService;
    }

    public AiActionResponse handle(AiActionRequest request) {
        String text = request.resolvedText();
        if (text.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "text 或 asrText 不能为空");
        }

        String tenantId = TenantContextHolder.require().tenantId();
        String customerId = request.context() == null ? null : request.context().currentCustomerId();

        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId);
        body.put("inputType", request.inputType().name());
        body.put("text", text);
        body.put("asrText", request.asrText());
        body.put("context", request.context() == null
                ? Map.of("currentItems", List.of())
                : request.context());
        body.put("candidateCustomers", safeCustomerCandidates());
        body.put("candidateProducts", safeProductCandidates(customerId));

        return aiServiceClient.parseAction(body);
    }

    private List<Map<String, Object>> safeCustomerCandidates() {
        try {
            return loadCustomerCandidates();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> safeProductCandidates(String customerId) {
        try {
            return loadProductCandidates(customerId);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> loadCustomerCandidates() {
        List<CustomerSummary> page = customerService
                .search(null, PageRequestParams.of(1, 50))
                .content();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CustomerSummary c : page) {
            Map<String, Object> row = new HashMap<>();
            row.put("customerId", c.customerId());
            row.put("customerName", c.customerName());
            row.put("aliases", c.aliases() == null ? List.of() : c.aliases());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> loadProductCandidates(String customerId) {
        List<ProductVariant> variants = productService.selector(null, customerId).results();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProductVariant p : variants) {
            Map<String, Object> row = new HashMap<>();
            row.put("itemCode", p.itemCode());
            row.put("productId", p.productId());
            row.put("productName", p.productName());
            row.put("spec", p.spec());
            row.put("aliases", p.aliases() == null ? List.of() : p.aliases());
            row.put("allowedUoms", p.allowedUoms() == null
                    ? List.of()
                    : p.allowedUoms().stream().map(AllowedUom::uom).toList());
            out.add(row);
        }
        return out;
    }
}
