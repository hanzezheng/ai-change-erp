package com.nongpi.assistant.erp.adapter;

import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.order.domain.Order;
import com.nongpi.assistant.order.domain.OrderStatus;
import com.nongpi.assistant.order.domain.OrderSummary;
import com.nongpi.assistant.pricing.domain.LastDealPrice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesOrderErpAdapter {

    Order createDraft(ErpConnection connection, SalesOrderWriteCommand command);

    Order updateDraft(ErpConnection connection, String orderId, Instant expectedModifiedAt, SalesOrderWriteCommand command);

    Order submit(ErpConnection connection, String orderId);

    Optional<Order> findById(ErpConnection connection, String orderId);

    List<OrderSummary> search(ErpConnection connection, OrderSearchQuery query);

    List<String> recentCustomerIds(ErpConnection connection, int limit);

    List<String> frequentItemCodes(ErpConnection connection, String customerId, int limit);

    Optional<LastDealPrice> findLastDealPrice(ErpConnection connection, String customerId, String itemCode, String uom);

    record SalesOrderWriteCommand(
            String customerId,
            LocalDate transactionDate,
            List<ItemWrite> items
    ) {
    }

    record ItemWrite(
            String orderItemId,
            String itemCode,
            BigDecimal qty,
            String uom,
            BigDecimal rate
    ) {
    }

    record OrderSearchQuery(
            String keyword,
            OrderStatus status,
            LocalDate from,
            LocalDate to,
            int offset,
            int limit
    ) {
    }
}
