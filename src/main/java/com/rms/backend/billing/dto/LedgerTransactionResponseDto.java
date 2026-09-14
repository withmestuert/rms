package com.rms.backend.billing.dto;

import com.rms.backend.billing.entity.TransactionType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransactionResponseDto {
    private Long id;
    private String referenceNumber;
    private String date;
    private TransactionType type;
    private String accountHead;
    private String description;
    private String tenantOrVendor;
    private Integer amount;
    private String paymentMode;
    private Long runningBalance;
    private LocalDateTime createdAt;
    private Long propertyId;
}
