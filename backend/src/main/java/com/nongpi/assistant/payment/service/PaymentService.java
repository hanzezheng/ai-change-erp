package com.nongpi.assistant.payment.service;

import com.nongpi.assistant.audit.AuditActions;
import com.nongpi.assistant.audit.AuditService;
import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.adapter.PaymentEntryErpAdapter;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.connection.ErpConnectionProvider;
import com.nongpi.assistant.order.domain.Order;
import com.nongpi.assistant.order.domain.OrderStatus;
import com.nongpi.assistant.order.service.OrderService;
import com.nongpi.assistant.payment.domain.Payment;
import com.nongpi.assistant.payment.domain.PaymentMethod;
import com.nongpi.assistant.payment.web.CreatePaymentRequest;
import com.nongpi.assistant.saas.idempotency.IdempotencyRecordEntity;
import com.nongpi.assistant.saas.idempotency.IdempotencyService;
import com.nongpi.assistant.saas.idempotency.IdempotencyStatus;
import com.nongpi.assistant.security.SecurityUtils;
import com.nongpi.assistant.security.UserPrincipal;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentEntryErpAdapter paymentEntryErpAdapter;
    private final ErpConnectionProvider erpConnectionProvider;
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    public PaymentService(PaymentEntryErpAdapter paymentEntryErpAdapter,
                          ErpConnectionProvider erpConnectionProvider,
                          OrderService orderService,
                          IdempotencyService idempotencyService,
                          AuditService auditService) {
        this.paymentEntryErpAdapter = paymentEntryErpAdapter;
        this.erpConnectionProvider = erpConnectionProvider;
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
    }

    public List<PaymentMethod> listMethods() {
        return paymentEntryErpAdapter.listPaymentMethods(connection());
    }

    public Payment createDraft(CreatePaymentRequest request, String idempotencyKey) {
        UserPrincipal actor = SecurityUtils.requireUser();
        ErpConnection connection = connection();
        String hash = idempotencyService.hash(request);
        IdempotencyRecordEntity record = idempotencyService.begin(
                actor.tenantId(), IdempotencyService.CREATE_PAYMENT, idempotencyKey, hash);
        if (record.getStatus() == IdempotencyStatus.SUCCEEDED) {
            return getById(record.getResourceId());
        }
        try {
            validate(connection, request);
        } catch (RuntimeException ex) {
            idempotencyService.abandon(record.getId());
            throw ex;
        }
        Payment created = idempotencyService.executeWrite(record,
                () -> paymentEntryErpAdapter.createDraftResource(connection, new PaymentEntryErpAdapter.PaymentWriteCommand(
                        request.customerId(),
                        request.relatedOrderId(),
                        request.amount(),
                        request.paymentMethodId(),
                        request.referenceNo(),
                        request.referenceDate())),
                this::getById);
        auditService.success(actor.tenantId(), actor.userId(), AuditActions.PAYMENT_DRAFT_CREATE,
                "PaymentEntry", created.paymentId(), Map.of(
                        "customerId", created.customerId() == null ? "" : created.customerId(),
                        "relatedOrderId", request.relatedOrderId(),
                        "amount", created.amount() == null ? BigDecimal.ZERO : created.amount()));
        return created;
    }

    public Payment confirm(String paymentId) {
        UserPrincipal actor = SecurityUtils.requireUser();
        Payment confirmed = paymentEntryErpAdapter.confirm(connection(), paymentId);
        auditService.success(actor.tenantId(), actor.userId(), AuditActions.PAYMENT_CONFIRM,
                "PaymentEntry", confirmed.paymentId(), Map.of(
                        "customerId", confirmed.customerId() == null ? "" : confirmed.customerId(),
                        "relatedOrderId", confirmed.relatedOrderId() == null ? "" : confirmed.relatedOrderId(),
                        "amount", confirmed.amount() == null ? BigDecimal.ZERO : confirmed.amount()));
        return confirmed;
    }

    public Payment getById(String paymentId) {
        return paymentEntryErpAdapter.findById(connection(), paymentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.PAYMENT_NOT_FOUND,
                        BusinessErrorCode.PAYMENT_NOT_FOUND.defaultMessage(), Map.of("paymentId", paymentId)));
    }

    public PageResponse<Payment> list(String relatedOrderId, PageRequestParams page) {
        if (relatedOrderId == null || relatedOrderId.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "必须提供 relatedOrderId");
        }
        List<Payment> all = paymentEntryErpAdapter.listByOrder(connection(), relatedOrderId);
        int from = Math.min(page.offset(), all.size());
        int to = Math.min(from + page.pageSize(), all.size());
        boolean hasMore = to < all.size();
        return PageResponse.of(all.subList(from, to), page.page(), page.pageSize(), hasMore);
    }

    private void validate(ErpConnection connection, CreatePaymentRequest request) {
        rejectUnsupportedNote(request.note());
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID, "收款金额必须大于 0");
        }
        Order order = orderService.getById(request.relatedOrderId());
        if (order.orderStatus() == OrderStatus.DRAFT || order.orderStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(BusinessErrorCode.ORDER_STATUS_INVALID,
                    "只能对已提交订单收款", Map.of("orderId", order.orderId(), "orderStatus", order.orderStatus().name()));
        }
        if (!request.customerId().equals(order.customerId())) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID,
                    "收款客户必须与订单客户一致",
                    Map.of("customerId", request.customerId(), "orderCustomerId", order.customerId()));
        }
        if (request.amount().compareTo(order.remainingToCollect()) > 0) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID,
                    "收款金额不能超过待收金额",
                    Map.of("amount", request.amount(), "remainingToCollect", order.remainingToCollect()));
        }
        boolean methodExists = paymentEntryErpAdapter.findConfiguredMethod(connection, request.paymentMethodId())
                .isPresent();
        if (!methodExists) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID, "付款方式不存在或当前公司未配置账户",
                    Map.of("paymentMethodId", request.paymentMethodId()));
        }
    }

    private void rejectUnsupportedNote(String note) {
        if (note != null && !note.isBlank()) {
            throw new BusinessException(BusinessErrorCode.UNSUPPORTED_FIELD, "当前版本暂不支持订单/收款备注");
        }
    }

    private ErpConnection connection() {
        return erpConnectionProvider.resolve(TenantContextHolder.require());
    }
}
