package com.nongpi.assistant.erp.adapter;

import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.payment.domain.Payment;
import com.nongpi.assistant.payment.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentEntryErpAdapter {

    List<PaymentMethod> listPaymentMethods(ErpConnection connection);

    boolean hasAccountForCompany(ErpConnection connection, String paymentMethodId, String company);

    Payment createDraft(ErpConnection connection, PaymentWriteCommand command);

    Payment confirm(ErpConnection connection, String paymentId);

    Optional<Payment> findById(ErpConnection connection, String paymentId);

    List<Payment> listByOrder(ErpConnection connection, String orderId);

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
