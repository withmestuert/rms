package com.rms.backend.billing.dto;

import com.rms.backend.billing.entity.InvoiceStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDto {
    private Long id;
    private String paymentReference;
    private Long invoiceId;
    private String invoiceNumber;
    private String tenantUid;
    private String tenantName;
    private Integer amount;
    private String paymentMode;
    private String transactionReference;
    private String idempotencyKey;
    private String status;
    private LocalDateTime createdAt;
    private Long propertyId;
    private Integer invoiceTotalAmount;
    private Integer invoicePaidAmount;
    private Integer invoiceRemainingBalance;
    private InvoiceStatus invoiceStatus;
}
