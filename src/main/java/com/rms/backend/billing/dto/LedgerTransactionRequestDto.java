package com.rms.backend.billing.dto;

import com.rms.backend.billing.entity.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransactionRequestDto {

    @NotNull(message = "Transaction type is required")
    private TransactionType type; // CREDIT or DEBIT

    @NotBlank(message = "Account head is required")
    private String accountHead; // e.g. "Maintenance Expense", "Vendor Payout", "Utility Payment"

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Tenant or vendor is required")
    private String tenantOrVendor;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than 0")
    private Integer amount;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode; // UPI, Cash, NEFT, Card

    private String date; // Defaults to today if blank
}
