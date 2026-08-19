package com.nongpi.assistant.erp.adapter.frappe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.common.money.Money;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
        return listConfiguredMethods(connection).stream()
                .map(ConfiguredPaymentMethod::toPublic)
                .toList();
    }

    @Override
    public Optional<ConfiguredPaymentMethod> findConfiguredMethod(ErpConnection connection, String paymentMethodId) {
        if (ErpValues.trimToNull(paymentMethodId) == null) {
            return Optional.empty();
        }
        return listConfiguredMethods(connection).stream()
                .filter(method -> paymentMethodId.equals(method.paymentMethodId()))
                .findFirst();
    }

    @Override
    public Payment createDraft(ErpConnection connection, PaymentWriteCommand command) {
        requireCompany(connection);
        ConfiguredPaymentMethod method = findConfiguredMethod(connection, command.paymentMethodId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.PAYMENT_METHOD_NOT_CONFIGURED,
                        BusinessErrorCode.PAYMENT_METHOD_NOT_CONFIGURED.defaultMessage(),
                        Map.of("paymentMethodId", command.paymentMethodId())));

        JsonNode generated = erpRestClient.callMethod(connection, GET_PAYMENT_ENTRY, Map.of(
                "dt", ErpSalesOrder.DOCTYPE,
                "dn", command.relatedOrderId(),
                "party_amount", command.amount()
        ));
        requireSameCurrencyReceive(generated);

        Map<String, Object> payload = toMap(generated);
        payload.put("doctype", ErpPaymentEntry.DOCTYPE);
        payload.put("mode_of_payment", method.paymentMethodId());
        payload.put("paid_to", method.defaultAccount());
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
        requireSameCurrencyReceive(created);
        if (!method.defaultAccount().equals(created.path("paid_to").asText(null))) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID,
                    "付款方式账户未能应用到收款单",
                    Map.of("expectedPaidTo", method.defaultAccount(),
                            "actualPaidTo", created.path("paid_to").asText("")));
        }
        if (created.path("difference_amount").decimalValue().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID,
                    "切换收款账户后金额不平衡，当前版本不支持该账户组合");
        }
        return mapPayment(created);
    }

    @Override
    public Payment confirm(ErpConnection connection, String paymentId) {
        JsonNode current = requireDoc(connection, paymentId);
        int docstatus = current.path("docstatus").asInt(-1);
        if (docstatus == 1) {
            requireSupportedReceive(connection, current);
            return mapPayment(current);
        }
        if (docstatus == 2) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_STATUS_INVALID,
                    "已取消的收款不能确认", Map.of("paymentId", paymentId));
        }
        if (docstatus != 0) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_STATUS_INVALID,
                    "当前收款状态不允许确认", Map.of("paymentId", paymentId, "docstatus", docstatus));
        }
        requireSupportedReceive(connection, current);
        requireRemainingAllows(connection, current);
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
        ErpQuery referenceQuery = ErpQuery.create()
                .fields("name", "parent", "reference_doctype", "reference_name", "allocated_amount")
                .filter(ErpFilter.eq("parenttype", ErpPaymentEntry.DOCTYPE))
                .filter(ErpFilter.eq("reference_doctype", ErpSalesOrder.DOCTYPE))
                .filter(ErpFilter.eq("reference_name", orderId))
                .parent(ErpPaymentEntry.DOCTYPE)
                .unlimited();
        List<String> paymentIds = erpRestClient.list(connection, ErpPaymentEntryReference.DOCTYPE,
                        referenceQuery, ErpPaymentEntryReference.class).stream()
                .map(ErpPaymentEntryReference::parent)
                .map(ErpValues::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Payment> payments = new ArrayList<>();
        for (String paymentId : paymentIds) {
            erpRestClient.getDocNode(connection, ErpPaymentEntry.DOCTYPE, paymentId)
                    .map(this::mapPayment)
                    .filter(payment -> orderId.equals(payment.relatedOrderId()))
                    .ifPresent(payments::add);
        }
        return payments;
    }

    private List<ConfiguredPaymentMethod> listConfiguredMethods(ErpConnection connection) {
        String company = ErpValues.trimToNull(connection.defaultCompany());
        if (company == null) {
            return List.of();
        }
        ErpQuery methodQuery = ErpQuery.create()
                .fields("name", "mode_of_payment", "type", "enabled")
                .filter(ErpFilter.eq("enabled", 1))
                .orderBy("name asc")
                .limit(0, 100);
        List<ErpModeOfPayment> methods = erpRestClient.list(
                connection, ErpModeOfPayment.DOCTYPE, methodQuery, ErpModeOfPayment.class);
        if (methods.isEmpty()) {
            return List.of();
        }
        ErpQuery accountQuery = ErpQuery.create()
                .fields("parent", "company", "default_account")
                .filter(ErpFilter.eq("parenttype", ErpModeOfPayment.DOCTYPE))
                .filter(ErpFilter.eq("company", company))
                .parent(ErpModeOfPayment.DOCTYPE)
                .unlimited();
        Map<String, String> accountByMethod = erpRestClient.list(
                        connection, ErpModeOfPaymentAccount.DOCTYPE, accountQuery, ErpModeOfPaymentAccount.class)
                .stream()
                .filter(row -> ErpValues.trimToNull(row.parent()) != null
                        && ErpValues.trimToNull(row.defaultAccount()) != null)
                .collect(Collectors.toMap(ErpModeOfPaymentAccount::parent,
                        ErpModeOfPaymentAccount::defaultAccount, (left, right) -> left, LinkedHashMap::new));
        List<ConfiguredPaymentMethod> configured = new ArrayList<>();
        for (ErpModeOfPayment mode : methods) {
            String id = ErpValues.trimToNull(mode.name());
            String account = id == null ? null : accountByMethod.get(id);
            if (id == null || account == null) {
                continue;
            }
            String label = ErpValues.trimToNull(mode.modeOfPayment());
            configured.add(new ConfiguredPaymentMethod(id, label == null ? id : label, account));
        }
        return configured;
    }

    private void requireSupportedReceive(ErpConnection connection, JsonNode payment) {
        String paymentType = payment.path("payment_type").asText("");
        String partyType = payment.path("party_type").asText("");
        String company = payment.path("company").asText("");
        if (!"Receive".equals(paymentType) || !"Customer".equals(partyType)) {
            throw unsupported("只支持客户收款", payment);
        }
        if (!Objects.equals(connection.defaultCompany(), company)) {
            throw unsupported("收款单不属于当前企业", payment);
        }
        List<JsonNode> salesOrders = salesOrderReferences(payment);
        if (salesOrders.size() != 1) {
            throw unsupported("当前版本只支持恰好关联一张销售订单的收款", payment);
        }
        for (JsonNode ref : payment.path("references")) {
            String doctype = ref.path("reference_doctype").asText("");
            if (!doctype.isBlank() && !ErpSalesOrder.DOCTYPE.equals(doctype)) {
                throw unsupported("当前版本不支持关联 " + doctype + " 的收款", payment);
            }
        }
        BigDecimal allocated = salesOrders.get(0).path("allocated_amount").decimalValue();
        if (allocated.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID, "收款分配金额必须大于 0");
        }
        String orderId = salesOrders.get(0).path("reference_name").asText("");
        JsonNode order = erpRestClient.getDocNode(connection, ErpSalesOrder.DOCTYPE, orderId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND,
                        BusinessErrorCode.ORDER_NOT_FOUND.defaultMessage(), Map.of("orderId", orderId)));
        int orderDocstatus = order.path("docstatus").asInt(-1);
        if (orderDocstatus == 2 || "Cancelled".equalsIgnoreCase(order.path("status").asText())) {
            throw new BusinessException(BusinessErrorCode.ORDER_STATUS_INVALID, "不能对已取消订单确认收款",
                    Map.of("orderId", orderId));
        }
        if (orderDocstatus != 1) {
            throw new BusinessException(BusinessErrorCode.ORDER_STATUS_INVALID, "只能对已提交订单确认收款",
                    Map.of("orderId", orderId, "docstatus", orderDocstatus));
        }
        if (!payment.path("party").asText("").equals(order.path("customer").asText(""))) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID, "收款客户必须与订单客户一致",
                    Map.of("customerId", payment.path("party").asText(""),
                            "orderCustomerId", order.path("customer").asText("")));
        }
    }

    private void requireRemainingAllows(ErpConnection connection, JsonNode payment) {
        JsonNode ref = salesOrderReferences(payment).get(0);
        String orderId = ref.path("reference_name").asText();
        BigDecimal allocated = ref.path("allocated_amount").decimalValue();
        JsonNode order = erpRestClient.getDocNode(connection, ErpSalesOrder.DOCTYPE, orderId).orElseThrow();
        BigDecimal total = order.path("grand_total").decimalValue();
        BigDecimal confirmedPaid = Money.zeroIfNull(order.path("advance_paid").decimalValue());
        BigDecimal remaining = Money.remainingToCollect(total, confirmedPaid);
        if (allocated.compareTo(remaining) > 0) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_INVALID,
                    "收款金额不能超过当前待收金额",
                    Map.of("allocatedAmount", allocated, "remainingToCollect", remaining));
        }
    }

    private void requireSameCurrencyReceive(JsonNode generated) {
        if (!"Receive".equals(generated.path("payment_type").asText())
                || !"Customer".equals(generated.path("party_type").asText())) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_NOT_SUPPORTED,
                    "当前版本只支持客户收款");
        }
        String fromCurrency = generated.path("paid_from_account_currency").asText(null);
        String toCurrency = generated.path("paid_to_account_currency").asText(null);
        if (fromCurrency != null && toCurrency != null && !fromCurrency.equals(toCurrency)) {
            throw new BusinessException(BusinessErrorCode.PAYMENT_NOT_SUPPORTED,
                    "当前版本只支持同币种订单收款");
        }
    }

    private static List<JsonNode> salesOrderReferences(JsonNode payment) {
        List<JsonNode> rows = new ArrayList<>();
        JsonNode references = payment.path("references");
        if (!references.isArray()) {
            return rows;
        }
        for (JsonNode ref : references) {
            if (ErpSalesOrder.DOCTYPE.equals(ref.path("reference_doctype").asText())) {
                rows.add(ref);
            }
        }
        return rows;
    }

    private BusinessException unsupported(String message, JsonNode payment) {
        return new BusinessException(BusinessErrorCode.PAYMENT_NOT_SUPPORTED, message,
                Map.of("paymentId", payment.path("name").asText("")));
    }

    private JsonNode requireDoc(ErpConnection connection, String paymentId) {
        return erpRestClient.getDocNode(connection, ErpPaymentEntry.DOCTYPE, paymentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.PAYMENT_NOT_FOUND,
                        BusinessErrorCode.PAYMENT_NOT_FOUND.defaultMessage(), Map.of("paymentId", paymentId)));
    }

    private Payment mapPayment(JsonNode node) {
        ErpPaymentEntry entry = objectMapper.convertValue(node, ErpPaymentEntry.class);
        List<ErpPaymentEntryReference> salesOrderRefs = entry.references() == null ? List.of()
                : entry.references().stream()
                .filter(ref -> ErpSalesOrder.DOCTYPE.equals(ref.referenceDoctype()))
                .toList();
        String relatedOrderId = salesOrderRefs.size() == 1
                ? ErpValues.trimToNull(salesOrderRefs.get(0).referenceName()) : null;
        BigDecimal amount = salesOrderRefs.size() == 1 && salesOrderRefs.get(0).allocatedAmount() != null
                ? salesOrderRefs.get(0).allocatedAmount()
                : (entry.paidAmount() != null ? entry.paidAmount() : entry.receivedAmount());
        PaymentConfirmationStatus status = PaymentConfirmationStatus.fromDocstatus(
                entry.docstatus() == null ? 0 : entry.docstatus());
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
