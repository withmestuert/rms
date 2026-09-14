package com.rms.backend.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", unique = true, nullable = false)
    private String invoiceNumber;

    @Column(name = "tenant_uid", nullable = false)
    private String tenantUid;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "room_no", nullable = false)
    private String roomNo;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "month_year", nullable = false)
    private String monthYear; // e.g. "October 2024"

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "due_date", nullable = false)
    private String dueDate; // YYYY-MM-DD

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status; // PENDING, PAID, OVERDUE

    @Column(name = "paid_on")
    private String paidOn; // YYYY-MM-DD

    @Column(name = "payment_mode")
    private String paymentMode; // UPI, Cash, Bank Transfer, Card

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = InvoiceStatus.PENDING;
        }
    }
}
