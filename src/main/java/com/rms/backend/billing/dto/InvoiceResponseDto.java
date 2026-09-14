package com.rms.backend.billing.dto;

import com.rms.backend.billing.entity.InvoiceStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponseDto {
    private Long id;
    private String invoiceNumber;
    private String tenantUid;
    private String tenantName;
    private String roomNo;
    private String monthYear;
    private Integer amount;
    private String dueDate;
    private InvoiceStatus status;
    private String paidOn;
    private String paymentMode;
    private String transactionRef;
    private LocalDateTime createdAt;
    private Long propertyId;
}
