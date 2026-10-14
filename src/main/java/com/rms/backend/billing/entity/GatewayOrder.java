package com.rms.backend.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true, nullable = false)
    private String orderId; // Razorpay Order ID (e.g. order_K...)

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "tenant_uid", nullable = false)
    private String tenantUid;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "amount", nullable = false)
    private Integer amount; // in Rupees

    @Column(name = "amount_in_paise", nullable = false)
    private Long amountInPaise;

    @Column(name = "currency", nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "gateway_provider", nullable = false)
    @Builder.Default
    private String gatewayProvider = "RAZORPAY";

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "CREATED"; // CREATED, PAID, FAILED, EXPIRED

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @Column(name = "payment_method")
    private String paymentMethod; // UPI, CARD, NETBANKING

    @Column(name = "vpa")
    private String vpa;

    @Column(name = "error_description")
    private String errorDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "CREATED";
        }
        if (currency == null) {
            currency = "INR";
        }
        if (gatewayProvider == null) {
            gatewayProvider = "RAZORPAY";
        }
    }
}
