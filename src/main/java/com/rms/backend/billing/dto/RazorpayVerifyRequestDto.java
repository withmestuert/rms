package com.rms.backend.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayVerifyRequestDto {

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotBlank(message = "paymentId is required")
    private String paymentId;

    @NotBlank(message = "signature is required")
    private String signature;

    private String paymentMethod; // UPI, CARD, NETBANKING
    private String vpa;            // UPI ID or UTR (e.g. user@okhdfcbank)
}
