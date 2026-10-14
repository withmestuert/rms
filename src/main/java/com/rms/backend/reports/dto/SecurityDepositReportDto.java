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
public class SecurityDepositReportDto {
    private long totalDepositsHeld;
    private int totalTenantsWithDeposits;
    private long pendingRefundLiability;
    private long netRetainedDeposits;

    private List<DepositItem> deposits;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepositItem {
        private String tenantUid;
        private String tenantName;
        private String mobileNumber;
        private String roomNo;
        private String bedNumber;
        private int depositAmount;
        private String advanceStatus; // PAID, PENDING
        private String checkInDate;
        private String vacateStatus;  // ACTIVE, NOTICE_PENDING, NOTICE_APPROVED, VACATED
        private double repayableEstimate;
    }
}
