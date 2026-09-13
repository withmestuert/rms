package com.rms.backend.billing.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleGenerationResultDto {
    private int generatedCount;
    private int skippedCount;
    private long totalAmount;
    private List<InvoiceResponseDto> generatedInvoices;
}
