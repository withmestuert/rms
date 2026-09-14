package com.rms.backend.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_number", unique = true, nullable = false)
    private String referenceNumber;

    @Column(name = "date", nullable = false)
    private String date; // YYYY-MM-DD

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type; // CREDIT, DEBIT

    @Column(name = "account_head", nullable = false)
    private String accountHead; // Rent Payment, Security Deposit, Maintenance Expense, etc.

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "tenant_or_vendor", nullable = false)
    private String tenantOrVendor;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "payment_mode", nullable = false)
    private String paymentMode; // UPI, Cash, NEFT, Card

    @Column(name = "running_balance", nullable = false)
    private Long runningBalance;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
