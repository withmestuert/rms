package com.rms.backend.reports.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportSummaryDto {
    private long totalTargetRent;
    private long totalCollectedRent;
    private double portfolioEfficiency;
    private int totalEnrolments;
    private int workingTenantsCount;
    private int otherTenantsCount;
    private int workingPercent;
    private int otherPercent;
    private int totalHometowns;
    private String topHometown;
    private int topHometownPercent;
}
