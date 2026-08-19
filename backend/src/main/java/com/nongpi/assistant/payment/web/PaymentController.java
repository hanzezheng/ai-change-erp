package com.nongpi.assistant.payment.web;

import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.payment.domain.Payment;
import com.nongpi.assistant.payment.domain.PaymentMethod;
import com.nongpi.assistant.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payment-methods")
    public List<PaymentMethod> methods() {
        return paymentService.listMethods();
    }

    @GetMapping("/payments")
    public PageResponse<Payment> list(@RequestParam(required = false) String relatedOrderId,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer pageSize) {
        return paymentService.list(relatedOrderId, PageRequestParams.of(page, pageSize));
    }

    @GetMapping("/payments/{paymentId}")
    public Payment detail(@PathVariable String paymentId) {
        return paymentService.getById(paymentId);
    }

    @PostMapping("/payments")
    public Payment create(@Valid @RequestBody CreatePaymentRequest request,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return paymentService.createDraft(request, idempotencyKey);
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public Payment confirm(@PathVariable String paymentId) {
        return paymentService.confirm(paymentId);
    }
}
