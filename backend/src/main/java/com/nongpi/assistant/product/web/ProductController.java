package com.nongpi.assistant.product.web;

import com.nongpi.assistant.product.domain.ProductSelectorResult;
import com.nongpi.assistant.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/selector")
    public ProductSelectorResult selector(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String customerId) {
        return productService.selector(q, customerId);
    }
}
