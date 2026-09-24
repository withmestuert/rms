package com.rms.backend.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordPaymentDto {

    @NotBlank(message = "Payment mode is required")
    private String paymentMode; // UPI, Cash, Bank Transfer, Card

    private String transactionRef; // UTR or receipt number

    private String paidOn; // YYYY-MM-DD, defaults to today if blank

    @Min(value = 1, message = "Payment amount must be greater than 0")
    private Integer amount; // Optional: if null, pays remaining balance

    private String idempotencyKey; // Optional: unique transaction idempotency key
}
