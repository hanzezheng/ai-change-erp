package com.nongpi.assistant.erp.adapter.frappe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.common.money.Money;
import com.nongpi.assistant.erp.adapter.SalesOrderErpAdapter;
import com.nongpi.assistant.erp.client.ErpFilter;
import com.nongpi.assistant.erp.client.ErpQuery;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.dto.ErpItem;
import com.nongpi.assistant.erp.dto.ErpItemAttribute;
import com.nongpi.assistant.erp.dto.ErpSalesOrder;
import com.nongpi.assistant.erp.dto.ErpSalesOrderItem;
import com.nongpi.assistant.erp.mapper.ErpDates;
import com.nongpi.assistant.erp.mapper.ErpSpec;
import com.nongpi.assistant.erp.mapper.ErpValues;
import com.nongpi.assistant.order.domain.Order;
import com.nongpi.assistant.order.domain.OrderItem;
import com.nongpi.assistant.order.domain.OrderStatus;
import com.nongpi.assistant.order.domain.OrderSummary;
import com.nongpi.assistant.order.domain.PaymentCollectionStatus;
import com.nongpi.assistant.pricing.domain.LastDealPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class FrappeSalesOrderErpAdapter implements SalesOrderErpAdapter {

    private static final String[] ORDER_FIELDS = {
            "name", "customer", "customer_name", "transaction_date", "company", "currency",
            "status", "docstatus", "grand_total", "total", "advance_paid", "creation", "modified"
    };
    private static final String[] ITEM_FIELDS = {
            "name", "parent", "item_code", "item_name", "qty", "uom", "rate", "amount", "idx"
    };

    private final ErpRestClient erpRestClient;
    private final ObjectMapper objectMapper;

    public FrappeSalesOrderErpAdapter(ErpRestClient erpRestClient, ObjectMapper objectMapper) {
        this.erpRestClient = erpRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Order createDraft(ErpConnection connection, SalesOrderWriteCommand command) {
        String orderId = createDraftResource(connection, command);
        return findById(connection, orderId).orElseThrow(() -> new BusinessException(
                BusinessErrorCode.ORDER_NOT_FOUND, BusinessErrorCode.ORDER_NOT_FOUND.defaultMessage(),
                Map.of("orderId", orderId)));
    }

    @Override
    public String createDraftResource(ErpConnection connection, SalesOrderWriteCommand command) {
        requireCompany(connection);
        JsonNode created = erpRestClient.createDoc(connection, ErpSalesOrder.DOCTYPE, toCreatePayload(connection, command));
        String orderId = ErpValues.trimToNull(created.path("name").asText(null));
        if (orderId == null) {
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR, "ERPNext 创建 Sales Order 未返回 name");
        }
        return orderId;
    }

    @Override
    public Order updateDraft(ErpConnection connection, String orderId, Instant expectedModifiedAt,
                             SalesOrderWriteCommand command) {
        requireCompany(connection);
        JsonNode current = requireDoc(connection, orderId);
        int docstatus = current.path("docstatus").asInt(-1);
        if (docstatus != 0) {
            throw new BusinessException(BusinessErrorCode.ORDER_STATUS_INVALID,
                    "只有草稿订单可以普通保存", Map.of("orderId", orderId, "docstatus", docstatus));
        }
        Instant currentModified = ErpDates.toInstant(current.path("modified").asText(null));
        if (expectedModifiedAt == null || currentModified == null || !expectedModifiedAt.equals(currentModified)) {
            throw new BusinessException(BusinessErrorCode.ORDER_CONFLICT);
        }
        Map<String, Object> payload = toMap(current);
        payload.put("doctype", ErpSalesOrder.DOCTYPE);
        payload.put("customer", command.customerId());
        payload.put("company", connection.defaultCompany());
        payload.put("transaction_date", ErpDates.toErpDate(command.transactionDate()));
        payload.put("delivery_date", ErpDates.toErpDate(command.transactionDate()));
        payload.put("items", toItemPayloads(command.items()));
        payload.put("modified", current.path("modified").asText());
        JsonNode updated = erpRestClient.updateDoc(connection, ErpSalesOrder.DOCTYPE, orderId, payload);
        return mapOrder(connection, updated);
    }

    @Override
    public Order submit(ErpConnection connection, String orderId) {
        JsonNode current = requireDoc(connection, orderId);
        int docstatus = current.path("docstatus").asInt(-1);
        if (docstatus == 1) {
            return mapOrder(connection, current);
        }
        if (docstatus != 0) {
            throw new BusinessException(BusinessErrorCode.ORDER_STATUS_INVALID,
                    "只有草稿订单可以提交", Map.of("orderId", orderId, "docstatus", docstatus));
        }
        if (!current.hasNonNull("doctype")) {
            ((ObjectNode) current).put("doctype", ErpSalesOrder.DOCTYPE);
        }
        JsonNode submitted = erpRestClient.submitDoc(connection, current);
        return mapOrder(connection, submitted);
    }

    @Override
    public Optional<Order> findById(ErpConnection connection, String orderId) {
        return erpRestClient.getDocNode(connection, ErpSalesOrder.DOCTYPE, orderId)
                .map(node -> mapOrder(connection, node));
    }

    @Override
    public List<OrderSummary> search(ErpConnection connection, OrderSearchQuery query) {
        ErpQuery erpQuery = ErpQuery.create()
                .fields(ORDER_FIELDS)
                .orderBy("transaction_date desc, modified desc")
                .limit(query.offset(), query.limit());
        applyStatus(erpQuery, query.status());
        if (query.from() != null) {
            erpQuery.filter(ErpFilter.greaterOrEqual("transaction_date", ErpDates.toErpDate(query.from())));
        }
        if (query.to() != null) {
            erpQuery.filter(ErpFilter.lessOrEqual("transaction_date", ErpDates.toErpDate(query.to())));
        }
        applyKeyword(connection, erpQuery, query.keyword());

        List<ErpSalesOrder> headers = erpRestClient.list(connection, ErpSalesOrder.DOCTYPE, erpQuery, ErpSalesOrder.class);
        Map<String, List<ErpSalesOrderItem>> itemsByParent = fetchItems(connection,
                headers.stream().map(ErpSalesOrder::name).filter(Objects::nonNull).toList());
        return headers.stream()
                .map(header -> toSummary(header, itemsByParent.getOrDefault(header.name(), List.of())))
                .toList();
    }

    @Override
    public List<String> recentCustomerIds(ErpConnection connection, int limit) {
        ErpQuery query = ErpQuery.create()
                .fields("customer")
                .filter(ErpFilter.eq("docstatus", 1))
                .orderBy("transaction_date desc, modified desc")
                .limit(0, Math.max(limit * 3, limit));
        List<String> ids = new ArrayList<>();
        for (ErpSalesOrder order : erpRestClient.list(connection, ErpSalesOrder.DOCTYPE, query, ErpSalesOrder.class)) {
            String customerId = ErpValues.trimToNull(order.customer());
            if (customerId != null && !ids.contains(customerId)) {
                ids.add(customerId);
            }
            if (ids.size() >= limit) {
                break;
            }
        }
        return ids;
    }

    @Override
    public List<String> frequentItemCodes(ErpConnection connection, String customerId, int limit) {
        ErpQuery orderQuery = ErpQuery.create()
                .fields("name")
                .filter(ErpFilter.eq("docstatus", 1))
                .filter(ErpFilter.eq("customer", customerId))
                .orderBy("transaction_date desc")
                .limit(0, 50);
        List<String> orderIds = erpRestClient.list(connection, ErpSalesOrder.DOCTYPE, orderQuery, ErpSalesOrder.class)
                .stream()
                .map(ErpSalesOrder::name)
                .filter(Objects::nonNull)
                .toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = fetchItems(connection, orderIds).values().stream()
                .flatMap(List::stream)
                .map(ErpSalesOrderItem::itemCode)
                .filter(code -> ErpValues.trimToNull(code) != null)
                .collect(Collectors.groupingBy(code -> code, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public Optional<LastDealPrice> findLastDealPrice(ErpConnection connection, String customerId, String itemCode, String uom) {
        ErpQuery orderQuery = ErpQuery.create()
                .fields("name", "transaction_date", "docstatus", "status")
                .filter(ErpFilter.eq("docstatus", 1))
                .filter(ErpFilter.eq("customer", customerId))
                .orderBy("transaction_date desc, modified desc")
                .limit(0, 50);
        List<ErpSalesOrder> orders = erpRestClient.list(connection, ErpSalesOrder.DOCTYPE, orderQuery, ErpSalesOrder.class)
                .stream()
                .filter(order -> !"Cancelled".equalsIgnoreCase(order.status()))
                .toList();
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        Map<String, ErpSalesOrder> byId = orders.stream()
                .collect(Collectors.toMap(ErpSalesOrder::name, order -> order, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<ErpSalesOrderItem>> items = fetchItems(connection, new ArrayList<>(byId.keySet()));
        for (ErpSalesOrder order : orders) {
            for (ErpSalesOrderItem row : items.getOrDefault(order.name(), List.of())) {
                if (itemCode.equals(row.itemCode()) && uom.equals(row.uom()) && row.rate() != null) {
                    Instant time = order.transactionDate() == null
                            ? null
                            : ErpDates.toLocalDate(order.transactionDate()).atStartOfDay(ErpDates.BUSINESS_ZONE).toInstant();
                    return Optional.of(new LastDealPrice(row.rate(), row.uom(), order.name(), time));
                }
            }
        }
        return Optional.empty();
    }

    private JsonNode requireDoc(ErpConnection connection, String orderId) {
        return erpRestClient.getDocNode(connection, ErpSalesOrder.DOCTYPE, orderId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND,
                        BusinessErrorCode.ORDER_NOT_FOUND.defaultMessage(), Map.of("orderId", orderId)));
    }

    private Order mapOrder(ErpConnection connection, JsonNode node) {
        ErpSalesOrder header = objectMapper.convertValue(node, ErpSalesOrder.class);
        List<ErpSalesOrderItem> items = header.items() == null ? List.of() : header.items();
        Map<String, String> productIds = productIds(connection, itemCodes(items));
        Map<String, String> specs = specs(connection, itemCodes(items));
        OrderStatus orderStatus = OrderStatus.fromErp(nullToZero(header.docstatus()), header.status());
        BigDecimal total = header.grandTotal() != null ? header.grandTotal() : header.total();
        BigDecimal confirmedPaid = Money.zeroIfNull(header.advancePaid());
        PaymentCollectionStatus paymentStatus = Money.collectionStatus(total, confirmedPaid);
        List<OrderItem> mappedItems = items.stream()
                .map(row -> new OrderItem(
                        row.name(),
                        productIds.getOrDefault(row.itemCode(), row.itemCode()),
                        row.itemCode(),
                        row.itemName(),
                        specs.get(row.itemCode()),
                        row.qty(),
                        row.uom(),
                        row.rate(),
                        row.amount()
                ))
                .toList();
        return new Order(
                header.name(),
                header.customer(),
                header.customerName(),
                ErpDates.toLocalDate(header.transactionDate()),
                mappedItems,
                orderStatus,
                orderStatus.label(),
                paymentStatus,
                paymentStatus.label(),
                total,
                confirmedPaid,
                Money.remainingToCollect(total, confirmedPaid),
                header.currency(),
                ErpDates.toInstant(header.creation()),
                ErpDates.toInstant(header.modified())
        );
    }

    private OrderSummary toSummary(ErpSalesOrder header, List<ErpSalesOrderItem> items) {
        OrderStatus orderStatus = OrderStatus.fromErp(nullToZero(header.docstatus()), header.status());
        BigDecimal total = header.grandTotal() != null ? header.grandTotal() : header.total();
        PaymentCollectionStatus paymentStatus = Money.collectionStatus(total, header.advancePaid());
        List<ErpSalesOrderItem> ordered = items.stream()
                .sorted((left, right) -> Integer.compare(
                        left.idx() == null ? Integer.MAX_VALUE : left.idx(),
                        right.idx() == null ? Integer.MAX_VALUE : right.idx()))
                .toList();
        String firstName = ordered.isEmpty() ? null : ordered.get(0).itemName();
        String itemSummary = firstName == null ? ""
                : ordered.size() == 1 ? firstName : firstName + " 等" + ordered.size() + "项";
        Instant transactionTime = header.transactionDate() == null
                ? ErpDates.toInstant(header.modified())
                : ErpDates.toLocalDate(header.transactionDate()).atStartOfDay(ErpDates.BUSINESS_ZONE).toInstant();
        return new OrderSummary(
                header.name(),
                header.customer(),
                header.customerName(),
                itemSummary,
                ordered.size(),
                total,
                orderStatus,
                paymentStatus,
                transactionTime
        );
    }

    private Map<String, List<ErpSalesOrderItem>> fetchItems(ErpConnection connection, List<String> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        ErpQuery query = ErpQuery.create()
                .fields(ITEM_FIELDS)
                .filter(ErpFilter.eq("parenttype", ErpSalesOrder.DOCTYPE))
                .filter(ErpFilter.in("parent", orderIds))
                .parent(ErpSalesOrder.DOCTYPE)
                .unlimited();
        return erpRestClient.list(connection, ErpSalesOrderItem.DOCTYPE, query, ErpSalesOrderItem.class).stream()
                .filter(row -> row.parent() != null)
                .collect(Collectors.groupingBy(ErpSalesOrderItem::parent, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, String> productIds(ErpConnection connection, List<String> itemCodes) {
        if (itemCodes.isEmpty()) {
            return Map.of();
        }
        ErpQuery query = ErpQuery.create()
                .fields("name", "item_code", "variant_of")
                .filter(ErpFilter.in("name", itemCodes))
                .unlimited();
        return erpRestClient.list(connection, ErpItem.DOCTYPE, query, ErpItem.class).stream()
                .collect(Collectors.toMap(
                        ErpItem::resolvedItemCode,
                        item -> ErpValues.trimToNull(item.variantOf()) == null
                                ? item.resolvedItemCode() : item.variantOf(),
                        (left, right) -> left));
    }

    private Map<String, String> specs(ErpConnection connection, List<String> itemCodes) {
        if (itemCodes.isEmpty()) {
            return Map.of();
        }
        ErpQuery query = ErpQuery.create()
                .fields("parent", "attribute", "attribute_value", "idx")
                .filter(ErpFilter.eq("parenttype", ErpItem.DOCTYPE))
                .filter(ErpFilter.in("parent", itemCodes))
                .parent(ErpItem.DOCTYPE)
                .unlimited();
        return erpRestClient.list(connection, ErpItemAttribute.DOCTYPE, query, ErpItemAttribute.class).stream()
                .filter(row -> row.parent() != null)
                .collect(Collectors.groupingBy(ErpItemAttribute::parent))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> ErpSpec.fromAttributes(entry.getValue())));
    }

    private void applyKeyword(ErpConnection connection, ErpQuery erpQuery, String keyword) {
        String pattern = FrappeSearch.likePattern(keyword);
        if (pattern == null) {
            return;
        }
        erpQuery.orFilter(ErpFilter.like("name", pattern))
                .orFilter(ErpFilter.like("customer_name", pattern))
                .orFilter(ErpFilter.like("customer", pattern));
        List<String> itemParents = itemParentsMatching(connection, pattern);
        if (!itemParents.isEmpty()) {
            erpQuery.orFilter(ErpFilter.in("name", itemParents));
        }
    }

    private List<String> itemParentsMatching(ErpConnection connection, String pattern) {
        ErpQuery query = ErpQuery.create()
                .fields("parent")
                .filter(ErpFilter.eq("parenttype", ErpSalesOrder.DOCTYPE))
                .parent(ErpSalesOrder.DOCTYPE)
                .orFilter(ErpFilter.like("item_code", pattern))
                .orFilter(ErpFilter.like("item_name", pattern))
                .limit(0, 50);
        return erpRestClient.list(connection, ErpSalesOrderItem.DOCTYPE, query, ErpSalesOrderItem.class).stream()
                .map(ErpSalesOrderItem::parent)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void applyStatus(ErpQuery query, OrderStatus status) {
        if (status == null) {
            return;
        }
        switch (status) {
            case DRAFT -> query.filter(ErpFilter.eq("docstatus", 0));
            case CANCELLED -> query.filter(ErpFilter.eq("docstatus", 2));
            case COMPLETED -> {
                query.filter(ErpFilter.eq("docstatus", 1));
                query.filter(ErpFilter.eq("status", "Completed"));
            }
            case SUBMITTED -> {
                query.filter(ErpFilter.eq("docstatus", 1));
                query.filter(ErpFilter.notEq("status", "Completed"));
            }
        }
    }

    private Map<String, Object> toCreatePayload(ErpConnection connection, SalesOrderWriteCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("doctype", ErpSalesOrder.DOCTYPE);
        payload.put("customer", command.customerId());
        payload.put("company", connection.defaultCompany());
        payload.put("transaction_date", ErpDates.toErpDate(command.transactionDate()));
        payload.put("delivery_date", ErpDates.toErpDate(command.transactionDate()));
        if (connection.sellingPriceList() != null) {
            payload.put("selling_price_list", connection.sellingPriceList());
        }
        payload.put("items", toItemPayloads(command.items()));
        return payload;
    }

    private List<Map<String, Object>> toItemPayloads(List<ItemWrite> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ItemWrite item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            if (ErpValues.trimToNull(item.orderItemId()) != null) {
                row.put("name", item.orderItemId());
            }
            row.put("item_code", item.itemCode());
            row.put("qty", item.qty());
            row.put("uom", item.uom());
            if (item.rate() != null) {
                row.put("rate", item.rate());
            }
            rows.add(row);
        }
        return rows;
    }

    private void requireCompany(ErpConnection connection) {
        if (ErpValues.trimToNull(connection.defaultCompany()) == null) {
            throw new BusinessException(BusinessErrorCode.ERP_WRITE_CONFIGURATION_INCOMPLETE,
                    "尚未配置 defaultCompany，无法写入 Sales Order");
        }
    }

    private List<String> itemCodes(List<ErpSalesOrderItem> items) {
        return items.stream()
                .map(ErpSalesOrderItem::itemCode)
                .filter(code -> ErpValues.trimToNull(code) != null)
                .distinct()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
