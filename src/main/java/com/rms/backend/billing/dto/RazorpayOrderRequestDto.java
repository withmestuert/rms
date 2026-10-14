package com.rms.backend.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayOrderRequestDto {

    @NotBlank(message = "invoiceNumber is required")
    private String invoiceNumber;

    /**
     * Optional custom payment amount. If null or 0, defaults to total pending invoice balance.
     */
    private Integer amount;
}
