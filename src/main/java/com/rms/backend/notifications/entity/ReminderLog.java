package com.rms.backend.notifications.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "tenant_uid", nullable = false)
    private String tenantUid;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "channel", nullable = false)
    @Builder.Default
    private String channel = "WHATSAPP"; // WHATSAPP, SMS, EMAIL

    @Column(name = "reminder_type", nullable = false)
    private String reminderType; // UPCOMING_DUE, DUE_TODAY, OVERDUE_MILD, OVERDUE_URGENT, FINAL_NOTICE

    @Column(name = "days_offset", nullable = false)
    @Builder.Default
    private Integer daysOffset = 0;

    @Column(name = "amount_due", nullable = false)
    private Integer amountDue;

    @Column(name = "message_body", nullable = false, columnDefinition = "TEXT")
    private String messageBody;

    @Column(name = "payment_link", nullable = false)
    private String paymentLink;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "SENT"; // QUEUED, SENT, DELIVERED, FAILED

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
        if (channel == null) {
            channel = "WHATSAPP";
        }
        if (status == null) {
            status = "SENT";
        }
    }
}
