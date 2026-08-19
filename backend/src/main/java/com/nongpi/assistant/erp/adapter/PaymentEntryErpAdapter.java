package com.nongpi.assistant.erp.adapter;

import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.payment.domain.Payment;
import com.nongpi.assistant.payment.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentEntryErpAdapter {

    /**
     * 当前 Company 下已配置 default_account、可用于 Customer Receive 的付款方式。
     * 未配置账户的方式不会出现在结果中。
     */
    List<PaymentMethod> listPaymentMethods(ErpConnection connection);

    Optional<ConfiguredPaymentMethod> findConfiguredMethod(ErpConnection connection, String paymentMethodId);

    Payment createDraft(ErpConnection connection, PaymentWriteCommand command);

    Payment confirm(ErpConnection connection, String paymentId);

    Optional<Payment> findById(ErpConnection connection, String paymentId);

    List<Payment> listByOrder(ErpConnection connection, String orderId);

    record ConfiguredPaymentMethod(
            String paymentMethodId,
            String paymentMethodName,
            String defaultAccount
    ) {
        public PaymentMethod toPublic() {
            return new PaymentMethod(paymentMethodId, paymentMethodName);
        }
    }

    record PaymentWriteCommand(
            String customerId,
            String relatedOrderId,
            BigDecimal amount,
            String paymentMethodId,
            String referenceNo,
            LocalDate referenceDate
    ) {
    }
}
