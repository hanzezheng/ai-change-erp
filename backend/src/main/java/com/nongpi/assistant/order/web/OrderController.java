package com.nongpi.assistant.order.web;

import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.order.domain.Order;
import com.nongpi.assistant.order.domain.OrderPaymentSummary;
import com.nongpi.assistant.order.domain.OrderStatus;
import com.nongpi.assistant.order.domain.OrderSummary;
import com.nongpi.assistant.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) OrderStatus status,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer pageSize) {
        return orderService.search(q, status, from, to, PageRequestParams.of(page, pageSize));
    }

    @GetMapping("/{orderId}")
    public Order detail(@PathVariable String orderId) {
        return orderService.getById(orderId);
    }

    @PostMapping
    public Order create(@Valid @RequestBody CreateOrderRequest request,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return orderService.createDraft(request, idempotencyKey);
    }

    @PutMapping("/{orderId}")
    public Order update(@PathVariable String orderId, @Valid @RequestBody UpdateOrderRequest request) {
        return orderService.updateDraft(orderId, request);
    }

    @PostMapping("/{orderId}/submit")
    public Order submit(@PathVariable String orderId) {
        return orderService.submit(orderId);
    }

    @GetMapping("/{orderId}/payment-summary")
    public OrderPaymentSummary paymentSummary(@PathVariable String orderId) {
        return orderService.paymentSummary(orderId);
    }
}
