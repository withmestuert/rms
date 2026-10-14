package com.rms.backend.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnoverAnalyticsDto {
    private double averageStayDurationMonths;
    private int totalAdmissionsCount;
    private int totalVacatesCount;
    private double retentionRate; // percentage

    private List<MonthlyFlowItem> monthlyFlow;
    private Map<String, Integer> vacateReasons;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyFlowItem {
        private String month;      // "May 2026"
        private int admissions;
        private int vacates;
        private int netGrowth;
    }
}
