package com.nongpi.assistant.erp.adapter.frappe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.adapter.PaymentEntryErpAdapter;
import com.nongpi.assistant.erp.client.ErpFilter;
import com.nongpi.assistant.erp.client.ErpQuery;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.dto.ErpModeOfPayment;
import com.nongpi.assistant.erp.dto.ErpModeOfPaymentAccount;
import com.nongpi.assistant.erp.dto.ErpPaymentEntry;
import com.nongpi.assistant.erp.dto.ErpPaymentEntryReference;
import com.nongpi.assistant.erp.dto.ErpSalesOrder;
import com.nongpi.assistant.erp.mapper.ErpDates;
import com.nongpi.assistant.erp.mapper.ErpValues;
import com.nongpi.assistant.payment.domain.Payment;
import com.nongpi.assistant.payment.domain.PaymentConfirmationStatus;
import com.nongpi.assistant.payment.domain.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FrappePaymentEntryErpAdapter implements PaymentEntryErpAdapter {

    private static final String GET_PAYMENT_ENTRY =
            "erpnext.accounts.doctype.payment_entry.payment_entry.get_payment_entry";

    private final ErpRestClient erpRestClient;
    private final ObjectMapper objectMapper;

    public FrappePaymentEntryErpAdapter(ErpRestClient erpRestClient, ObjectMapper objectMapper) {
        this.erpRestClient = erpRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PaymentMethod> listPaymentMethods(ErpConnection connection) {
        ErpQuery query = ErpQuery.create()
                .fields("name", "mode_of_payment", "type", "enabled")
                .filter(ErpFilter.eq("enabled", 1))
                .orderBy("name asc")
                .limit(0, 100);
        return erpRestClient.list(connection, ErpModeOfPayment.DOCTYPE, query, ErpModeOfPayment.class).stream()
                .map(mode -> {
                    String id = ErpValues.trimToNull(mode.name());
                    String label = ErpValues.trimToNull(mode.modeOfPayment());
                    return new PaymentMethod(id, label == null ? id : label);
                })
                .filter(method -> method.paymentMethodId() != null)
                .toList();
    }

    @Override
    public boolean hasAccountForCompany(ErpConnection connection, String paymentMethodId, String company) {
        if (ErpValues.trimToNull(paymentMethodId) == null || ErpValues.trimToNull(company) == null) {
            return false;
        }
        ErpQuery query = ErpQuery.create()
                .fields("parent", "company", "default_account")
                .filter(ErpFilter.eq("parenttype", ErpModeOfPayment.DOCTYPE))
                .filter(ErpFilter.eq("parent", paymentMethodId))
                .filter(ErpFilter.eq("company", company))
                .parent(ErpModeOfPayment.DOCTYPE)
                .unlimited();
        return erpRestClient.list(connection, ErpModeOfPaymentAccount.DOCTYPE, query, ErpModeOfPaymentAccount.class)
                .stream()
                .anyMatch(row -> ErpValues.trimToNull(row.defaultAccount()) != null);
    }

    @Override
    public Payment createDraft(ErpConnection connection, PaymentWriteCommand command) {
        requireCompany(connection);
        JsonNode generated = erpRestClient.callMethod(connection, GET_PAYMENT_ENTRY, Map.of(
                "dt", ErpSalesOrder.DOCTYPE,
                "dn", command.relatedOrderId(),
                "party_amount", command.amount()
        ));
        Map<String, Object> payload = toMap(generated);
        payload.put("doctype", ErpPaymentEntry.DOCTYPE);
        payload.put("mode_of_payment", command.paymentMethodId());
        payload.put("paid_amount", command.amount());
        payload.put("received_amount", command.amount());
        payload.put("party", command.customerId());
        if (command.referenceNo() != null) {
            payload.put("reference_no", command.referenceNo());
        }
        if (command.referenceDate() != null) {
            payload.put("reference_date", ErpDates.toErpDate(command.referenceDate()));
        }
        payload.remove("__islocal");
        payload.remove("__unsaved");
        JsonNode created = erpRestClient.createDoc(connection, ErpPaymentEntry.DOCTYPE, payload);
        return mapPayment(created);
    }

    @Override
    public Payment confirm(ErpConnection connection, String paymentId) {
        JsonNode current = requireDoc(connection, paymentId);
        int docstatus = current.path("docstatus").asInt(-1);
        if (docstatus == 1) {
            return mapPayment(current);
        }
        if (docstatus == 2) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_STATUS_INVALID,
                    "已取消的收款不能确认", Map.of("paymentId", paymentId));
        }
        if (!current.hasNonNull("doctype")) {
            ((ObjectNode) current).put("doctype", ErpPaymentEntry.DOCTYPE);
        }
        JsonNode submitted = erpRestClient.submitDoc(connection, current);
        return mapPayment(submitted);
    }

    @Override
    public Optional<Payment> findById(ErpConnection connection, String paymentId) {
        return erpRestClient.getDocNode(connection, ErpPaymentEntry.DOCTYPE, paymentId)
                .map(this::mapPayment);
    }

    @Override
    public List<Payment> listByOrder(ErpConnection connection, String orderId) {
        ErpQuery query = ErpQuery.create()
                .fields("name", "party", "party_name", "paid_amount", "mode_of_payment",
                        "reference_no", "reference_date", "docstatus", "creation", "modified")
                .orderBy("creation desc")
                .limit(0, 50);
        return erpRestClient.list(connection, ErpPaymentEntry.DOCTYPE, query, ErpPaymentEntry.class).stream()
                .map(entry -> erpRestClient.getDocNode(connection, ErpPaymentEntry.DOCTYPE, entry.name()))
                .flatMap(Optional::stream)
                .map(this::mapPayment)
                .filter(payment -> orderId.equals(payment.relatedOrderId()))
                .toList();
    }

    private JsonNode requireDoc(ErpConnection connection, String paymentId) {
        return erpRestClient.getDocNode(connection, ErpPaymentEntry.DOCTYPE, paymentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.PAYMENT_NOT_FOUND,
                        BusinessErrorCode.PAYMENT_NOT_FOUND.defaultMessage(), Map.of("paymentId", paymentId)));
    }

    private Payment mapPayment(JsonNode node) {
        ErpPaymentEntry entry = objectMapper.convertValue(node, ErpPaymentEntry.class);
        String relatedOrderId = null;
        if (entry.references() != null) {
            relatedOrderId = entry.references().stream()
                    .filter(ref -> ErpSalesOrder.DOCTYPE.equals(ref.referenceDoctype()))
                    .map(ErpPaymentEntryReference::referenceName)
                    .filter(name -> ErpValues.trimToNull(name) != null)
                    .findFirst()
                    .orElse(null);
        }
        PaymentConfirmationStatus status = PaymentConfirmationStatus.fromDocstatus(
                entry.docstatus() == null ? 0 : entry.docstatus());
        BigDecimal amount = entry.paidAmount() != null ? entry.paidAmount() : entry.receivedAmount();
        return new Payment(
                entry.name(),
                entry.party(),
                entry.partyName(),
                relatedOrderId,
                amount,
                entry.modeOfPayment(),
                entry.modeOfPayment(),
                status,
                status.label(),
                entry.referenceNo(),
                ErpDates.toLocalDate(entry.referenceDate()),
                ErpDates.toInstant(entry.creation()),
                ErpDates.toInstant(entry.modified())
        );
    }

    private void requireCompany(ErpConnection connection) {
        if (ErpValues.trimToNull(connection.defaultCompany()) == null) {
            throw new BusinessException(BusinessErrorCode.ERP_WRITE_CONFIGURATION_INCOMPLETE,
                    "尚未配置 defaultCompany，无法写入 Payment Entry");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return new LinkedHashMap<>(objectMapper.convertValue(node, Map.class));
    }
}
