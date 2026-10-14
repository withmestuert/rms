package com.rms.backend.billing.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicInvoiceDto {

    private Long id;
    private String invoiceNumber;
    private String tenantUid;
    private String tenantName;
    private String tenantPhone;
    private String roomNo;
    private String monthYear;
    private Integer amount;
    private Integer paidAmount;
    private Integer pendingAmount;
    private String dueDate;
    private String status; // PENDING, PARTIALLY_PAID, PAID, OVERDUE
    private String paidOn;
    private String paymentMode;
    private String transactionRef;
    private Long propertyId;
    private String propertyName;
    private String propertyAddress;
    private String propertyContact;
}
