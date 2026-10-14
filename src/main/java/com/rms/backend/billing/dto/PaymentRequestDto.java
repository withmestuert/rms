package com.rms.backend.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestDto {

    @NotNull(message = "Invoice ID is required")
    private Long invoiceId;

    private String tenantUid;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Payment amount must be greater than 0")
    private Integer amount;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode; // CASH, UPI, CARD, BANK_TRANSFER

    private String transactionReference;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String paidOn; // YYYY-MM-DD

    private Long propertyId;
}
