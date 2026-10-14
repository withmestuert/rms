package com.rms.backend.billing.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayOrderResponseDto {

    private String orderId;
    private String keyId;
    private Long amountInPaise;
    private Integer amountInRupees;
    private String currency;
    private String invoiceNumber;
    private String tenantName;
    private String tenantContact;
    private String companyName;
    private String description;
    private boolean simulationMode;
}
