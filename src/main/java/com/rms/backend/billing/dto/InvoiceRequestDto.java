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
public class InvoiceRequestDto {

    @NotBlank(message = "Tenant UID is required")
    private String tenantUid;

    @NotBlank(message = "Month/Year is required")
    private String monthYear; // e.g. "October 2024"

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than 0")
    private Integer amount;

    @NotBlank(message = "Due date is required")
    private String dueDate; // YYYY-MM-DD

    private String paymentMode;
}
