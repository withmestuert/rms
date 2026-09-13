package com.rms.backend.billing.dto;

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
}
