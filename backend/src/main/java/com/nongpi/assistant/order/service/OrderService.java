package com.nongpi.assistant.order.service;

import com.nongpi.assistant.audit.AuditActions;
import com.nongpi.assistant.audit.AuditService;
import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.customer.service.CustomerService;
import com.nongpi.assistant.erp.adapter.SalesOrderErpAdapter;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.connection.ErpConnectionProvider;
import com.nongpi.assistant.erp.mapper.ErpDates;
import com.nongpi.assistant.erp.mapper.ErpValues;
import com.nongpi.assistant.order.domain.Order;
import com.nongpi.assistant.order.domain.OrderItem;
import com.nongpi.assistant.order.domain.OrderPaymentSummary;
import com.nongpi.assistant.order.domain.OrderStatus;
import com.nongpi.assistant.order.domain.OrderSummary;
import com.nongpi.assistant.order.web.CreateOrderRequest;
import com.nongpi.assistant.order.web.OrderItemRequest;
import com.nongpi.assistant.order.web.UpdateOrderRequest;
import com.nongpi.assistant.product.service.ProductService;
import com.nongpi.assistant.saas.idempotency.IdempotencyRecordEntity;
import com.nongpi.assistant.saas.idempotency.IdempotencyService;
import com.nongpi.assistant.saas.idempotency.IdempotencyStatus;
import com.nongpi.assistant.security.SecurityUtils;
import com.nongpi.assistant.security.UserPrincipal;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final SalesOrderErpAdapter salesOrderErpAdapter;
    private final ErpConnectionProvider erpConnectionProvider;
    private final CustomerService customerService;
    private final ProductService productService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final Clock clock;

    public OrderService(SalesOrderErpAdapter salesOrderErpAdapter,
                        ErpConnectionProvider erpConnectionProvider,
                        CustomerService customerService,
                        ProductService productService,
                        IdempotencyService idempotencyService,
                        AuditService auditService,
                        Clock clock) {
        this.salesOrderErpAdapter = salesOrderErpAdapter;
        this.erpConnectionProvider = erpConnectionProvider;
        this.customerService = customerService;
        this.productService = productService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public Order createDraft(CreateOrderRequest request, String idempotencyKey) {
        UserPrincipal actor = SecurityUtils.requireUser();
        ErpConnection connection = connection();
        rejectUnsupportedNote(request.note());
        requireEmptyOrderItemIds(request.items());
        validateWrite(request.customerId(), request.items());
        LocalDate transactionDate = request.transactionDate() == null
                ? ErpDates.today(clock) : request.transactionDate();
        SalesOrderErpAdapter.SalesOrderWriteCommand command = toCommand(request.customerId(), transactionDate, request.items());

        String hash = idempotencyService.hash(request);
        IdempotencyRecordEntity record = idempotencyService.begin(
                actor.tenantId(), IdempotencyService.CREATE_ORDER, idempotencyKey, hash);
        if (record.getStatus() == IdempotencyStatus.SUCCEEDED) {
            return getById(record.getResourceId());
        }
        Order created = idempotencyService.executeWrite(record,
                () -> salesOrderErpAdapter.createDraft(connection, command),
                Order::orderId);
        auditService.success(actor.tenantId(), actor.userId(), AuditActions.ORDER_DRAFT_CREATE,
                "SalesOrder", created.orderId(), summary(created));
        return created;
    }

    public Order updateDraft(String orderId, UpdateOrderRequest request) {
        UserPrincipal actor = SecurityUtils.requireUser();
        rejectUnsupportedNote(request.note());
        validateWrite(request.customerId(), request.items());
        validatePutOrderItemIds(orderId, request.items());
        Order updated = salesOrderErpAdapter.updateDraft(connection(), orderId, request.expectedModifiedAt(),
                toCommand(request.customerId(), request.transactionDate(), request.items()));
        auditService.success(actor.tenantId(), actor.userId(), AuditActions.ORDER_DRAFT_UPDATE,
                "SalesOrder", updated.orderId(), summary(updated));
        return updated;
    }

    public Order submit(String orderId) {
        UserPrincipal actor = SecurityUtils.requireUser();
        Order submitted = salesOrderErpAdapter.submit(connection(), orderId);
        auditService.success(actor.tenantId(), actor.userId(), AuditActions.ORDER_SUBMIT,
                "SalesOrder", submitted.orderId(), summary(submitted));
        return submitted;
    }

    public Order getById(String orderId) {
        return salesOrderErpAdapter.findById(connection(), orderId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND,
                        BusinessErrorCode.ORDER_NOT_FOUND.defaultMessage(), Map.of("orderId", orderId)));
    }

    public PageResponse<OrderSummary> search(String q, OrderStatus status, LocalDate from, LocalDate to,
                                             PageRequestParams page) {
        List<OrderSummary> fetched = salesOrderErpAdapter.search(connection(),
                new SalesOrderErpAdapter.OrderSearchQuery(q, status, from, to, page.offset(), page.pageSize() + 1));
        boolean hasMore = fetched.size() > page.pageSize();
        List<OrderSummary> content = hasMore ? fetched.subList(0, page.pageSize()) : fetched;
        return PageResponse.of(content, page.page(), page.pageSize(), hasMore);
    }

    public OrderPaymentSummary paymentSummary(String orderId) {
        Order order = getById(orderId);
        return new OrderPaymentSummary(
                order.totalAmount(),
                order.confirmedPaid(),
                order.remainingToCollect(),
                order.paymentStatus()
        );
    }

    private void rejectUnsupportedNote(String note) {
        if (note != null && !note.isBlank()) {
            throw new BusinessException(BusinessErrorCode.UNSUPPORTED_FIELD, "当前版本暂不支持订单/收款备注");
        }
    }

    private void requireEmptyOrderItemIds(List<OrderItemRequest> items) {
        for (OrderItemRequest item : items) {
            if (ErpValues.trimToNull(item.orderItemId()) != null) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST,
                        "新建订单不能指定 orderItemId", Map.of("orderItemId", item.orderItemId()));
            }
        }
    }

    private void validatePutOrderItemIds(String orderId, List<OrderItemRequest> items) {
        Order current = getById(orderId);
        Set<String> existing = current.items() == null ? Set.of()
                : current.items().stream()
                .map(OrderItem::orderItemId)
                .filter(id -> ErpValues.trimToNull(id) != null)
                .collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            String orderItemId = ErpValues.trimToNull(item.orderItemId());
            if (orderItemId == null) {
                continue;
            }
            if (!seen.add(orderItemId)) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST,
                        "同一请求中 orderItemId 不能重复", Map.of("orderItemId", orderItemId));
            }
            if (!existing.contains(orderItemId)) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST,
                        "orderItemId 不属于当前订单",
                        Map.of("orderItemId", orderItemId, "orderId", orderId));
            }
        }
    }

    private void validateWrite(String customerId, List<OrderItemRequest> items) {
        customerService.getById(customerId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.ORDER_INVALID, "订单至少需要一个商品");
        }
        for (OrderItemRequest item : items) {
            validateItem(item);
        }
    }

    private void validateItem(OrderItemRequest item) {
        productService.requireOrderableItem(item.itemCode());
        productService.requireAllowedUom(item.itemCode(), item.uom());
        if (item.qty() == null || item.qty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BusinessErrorCode.INVALID_QUANTITY,
                    BusinessErrorCode.INVALID_QUANTITY.defaultMessage(),
                    Map.of("itemCode", item.itemCode(), "qty", String.valueOf(item.qty())));
        }
        if (item.rate() != null && item.rate().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(BusinessErrorCode.INVALID_RATE,
                    BusinessErrorCode.INVALID_RATE.defaultMessage(),
                    Map.of("itemCode", item.itemCode(), "rate", item.rate()));
        }
    }

    private SalesOrderErpAdapter.SalesOrderWriteCommand toCommand(String customerId, LocalDate date,
                                                                  List<OrderItemRequest> items) {
        List<SalesOrderErpAdapter.ItemWrite> mapped = new ArrayList<>();
        for (OrderItemRequest item : items) {
            mapped.add(new SalesOrderErpAdapter.ItemWrite(
                    item.orderItemId(), item.itemCode(), item.qty(), item.uom(), item.rate()));
        }
        return new SalesOrderErpAdapter.SalesOrderWriteCommand(customerId, date, mapped);
    }

    private ErpConnection connection() {
        return erpConnectionProvider.resolve(TenantContextHolder.require());
    }

    private static Map<String, Object> summary(Order order) {
        return Map.of(
                "customerId", order.customerId() == null ? "" : order.customerId(),
                "itemCount", order.items() == null ? 0 : order.items().size(),
                "totalAmount", order.totalAmount() == null ? BigDecimal.ZERO : order.totalAmount()
        );
    }
}
