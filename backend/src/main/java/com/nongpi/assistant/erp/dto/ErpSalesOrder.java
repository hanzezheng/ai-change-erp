package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpSalesOrder(
        @JsonProperty("name") String name,
        @JsonProperty("customer") String customer,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("transaction_date") String transactionDate,
        @JsonProperty("company") String company,
        @JsonProperty("currency") String currency,
        @JsonProperty("status") String status,
        @JsonProperty("docstatus") Integer docstatus,
        @JsonProperty("grand_total") BigDecimal grandTotal,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("advance_paid") BigDecimal advancePaid,
        @JsonProperty("creation") String creation,
        @JsonProperty("modified") String modified,
        @JsonProperty("items") List<ErpSalesOrderItem> items
) {
    public static final String DOCTYPE = "Sales Order";
}
