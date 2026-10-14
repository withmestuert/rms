package com.rms.backend.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsKpiDto {
    private int totalBeds;
    private int occupiedBeds;
    private int reservedBeds;
    private int availableBeds;
    private int maintenanceBeds;
    private double occupancyRate; // percentage (0.0 to 100.0)

    private long totalBilledAmount;
    private long totalCollectedAmount;
    private long totalPendingAmount;
    private double collectionEfficiency; // percentage (0.0 to 100.0)

    // Accurate yield & RevPAB taking into account varying bed rents
    private double potentialMonthlyRevenue; // SUM(bed standard rents)
    private double potentialRevPab;         // Potential Monthly Revenue / Total Beds
    private double realizedRevPab;          // Actual Collected or Billed / Total Beds
    private double monthlyLostRevenue;      // Revenue lost due to vacant or maintenance beds
    private double yieldRealizationRate;    // (Realized / Potential) * 100

    private int activeTenantsCount;
}
