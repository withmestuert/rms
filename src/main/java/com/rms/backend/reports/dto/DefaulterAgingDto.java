package com.rms.backend.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaulterAgingDto {

    private AgingSummary summary;
    private List<DefaulterItem> defaulters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgingSummary {
        private long currentOverdue;       // 0 - 15 days
        private long overdue16to30;        // 16 - 30 days
        private long overdue31to60;        // 31 - 60 days
        private long overdue60Plus;        // 60+ days
        private long totalOverdueAmount;
        private int totalDefaultersCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefaulterItem {
        private String tenantUid;
        private String tenantName;
        private String mobileNumber;
        private String roomNo;
        private String bedNumber;
        private Long bedId;
        private String invoiceNumber;
        private String monthYear;
        private String dueDate;
        private int amount;
        private int paidAmount;
        private int pendingAmount;
        private int daysOverdue;
        private String agingCategory; // "0-15 Days", "16-30 Days", "31-60 Days", "60+ Days"
    }
}
