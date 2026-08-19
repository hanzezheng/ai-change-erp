package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpPaymentEntry(
        @JsonProperty("name") String name,
        @JsonProperty("payment_type") String paymentType,
        @JsonProperty("party_type") String partyType,
        @JsonProperty("party") String party,
        @JsonProperty("party_name") String partyName,
        @JsonProperty("company") String company,
        @JsonProperty("paid_from") String paidFrom,
        @JsonProperty("paid_to") String paidTo,
        @JsonProperty("paid_amount") BigDecimal paidAmount,
        @JsonProperty("received_amount") BigDecimal receivedAmount,
        @JsonProperty("paid_from_account_currency") String paidFromAccountCurrency,
        @JsonProperty("paid_to_account_currency") String paidToAccountCurrency,
        @JsonProperty("mode_of_payment") String modeOfPayment,
        @JsonProperty("reference_no") String referenceNo,
        @JsonProperty("reference_date") String referenceDate,
        @JsonProperty("docstatus") Integer docstatus,
        @JsonProperty("difference_amount") BigDecimal differenceAmount,
        @JsonProperty("creation") String creation,
        @JsonProperty("modified") String modified,
        @JsonProperty("references") List<ErpPaymentEntryReference> references
) {
    public static final String DOCTYPE = "Payment Entry";
}
