package com.rms.backend.notifications.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderStatsDto {

    private long totalPendingInvoices;
    private long totalPendingAmount;
    private long dueTodayCount;
    private long upcomingCount;
    private long overdueMildCount;
    private long overdueCriticalCount;
    private long remindersSentToday;
    private long totalRemindersLogged;
}
