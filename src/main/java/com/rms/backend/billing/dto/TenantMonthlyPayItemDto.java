package com.rms.backend.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMonthlyPayItemDto {
    private Long id;                  // Invoice ID
    private String invoiceNumber;     // e.g. "INV-2026-1001"
    private String tenantUid;
    private String tenantName;
    private String roomNo;
    private String bedNumber;
    private String mobileNumber;
    private String monthYear;          // "October 2024"
    private Integer billedAmount;      // e.g. 7000
    private Integer paidAmount;        // e.g. 7000
    private Integer pendingAmount;     // e.g. 0
    private String paidOn;             // "2024-10-05" or "-"
    private String paymentMode;        // "UPI", "Cash", "Bank Transfer", etc.
    private String upiDetails;         // UTR / UPI reference ID (e.g. "UPI-984729184" or UTR number)
    private String transactionRef;     // Any raw transaction reference
    private String status;             // "PAID", "PARTIALLY_PAID", "PENDING", "OVERDUE"
    private String dueDate;            // "2024-10-10"
}
