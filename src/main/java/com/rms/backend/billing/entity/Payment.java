package com.rms.backend.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = {"idempotency_key"}),
    @UniqueConstraint(name = "uk_payments_payment_ref", columnNames = {"payment_reference"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", nullable = false)
    private String paymentReference; // e.g. PAY-2026-1001

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "tenant_uid", nullable = false)
    private String tenantUid;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "payment_mode", nullable = false)
    private String paymentMode; // CASH, UPI, CARD, BANK_TRANSFER, OTHER

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "status", nullable = false)
    private String status; // SUCCESS, FAILED

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "property_id")
    private Long propertyId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "SUCCESS";
        }
    }
}
