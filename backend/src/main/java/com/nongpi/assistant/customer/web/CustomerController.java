package com.nongpi.assistant.customer.web;

import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.customer.domain.CustomerSelectorResult;
import com.nongpi.assistant.customer.domain.CustomerSummary;
import com.nongpi.assistant.customer.service.CustomerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public PageResponse<CustomerSummary> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer pageSize) {
        return customerService.search(q, PageRequestParams.of(page, pageSize));
    }

    @GetMapping("/selector")
    public CustomerSelectorResult selector(@RequestParam(required = false) String q) {
        return customerService.selector(q);
    }

    @GetMapping("/{customerId}")
    public CustomerSummary detail(@PathVariable String customerId) {
        return customerService.getById(customerId);
    }
}
