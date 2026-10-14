package com.rms.backend.notifications.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentReminderItemDto {

    private Long invoiceId;
    private String invoiceNumber;
    private String tenantUid;
    private String tenantName;
    private String mobileNumber;
    private String roomNo;
    private String monthYear;
    private Integer totalAmount;
    private Integer paidAmount;
    private Integer pendingAmount;
    private String dueDate;
    private String reminderType; // UPCOMING_DUE, DUE_TODAY, OVERDUE_MILD, OVERDUE_URGENT, FINAL_NOTICE
    private Integer daysOffset;  // e.g. -2 (due in 2 days), 0 (due today), +5 (5 days overdue)
    private String urgency;      // LOW, NORMAL, HIGH, CRITICAL
    private String paymentLink;
    private String formattedWhatsAppMessage;
    private String clickToChatUrl;
    private LocalDateTime lastReminderSentAt;
    private Integer totalRemindersSent;
    private Long propertyId;
    private String propertyName;
}
