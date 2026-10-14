package com.rms.backend.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTrendDto {
    private String month;       // "2026-05"
    private String monthLabel;  // "May 2026"
    private long billedAmount;
    private long collectedAmount;
    private long pendingAmount;
    private double collectionRate;
    private int paymentsCount;
}
