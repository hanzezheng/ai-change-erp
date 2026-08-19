package com.nongpi.assistant.pricing.web;

import com.nongpi.assistant.pricing.domain.LastDealPrice;
import com.nongpi.assistant.pricing.service.PricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/last-deal")
    public ResponseEntity<?> lastDeal(@RequestParam String customerId,
                                      @RequestParam String itemCode,
                                      @RequestParam String uom) {
        LastDealPrice price = pricingService.lastDeal(customerId, itemCode, uom);
        return ResponseEntity.ok(price == null ? Map.of() : price);
    }
}
