package com.rms.backend.vacate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vacate_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", unique = true, nullable = false)
    private String requestId; // e.g. VR-2026-001

    @Column(name = "tenant_uid")
    private String tenantUid;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "room_no", nullable = false)
    private String roomNo;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "aadhaar_no")
    private String aadhaarNo;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "expected_leaving_date", nullable = false)
    private LocalDate expectedLeavingDate;

    @Column(name = "notice_days", nullable = false)
    private Integer noticeDays;

    @Column(name = "advance_paid", nullable = false)
    private Double advancePaid;

    @Column(name = "maintenance_charge")
    @Builder.Default
    private Double maintenanceCharge = 0.0;

    @Column(name = "breakage_charge")
    @Builder.Default
    private Double breakageCharge = 0.0;

    @Column(name = "advance_repayable", nullable = false)
    private Double advanceRepayable;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VacateStatus status;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = VacateStatus.PENDING;
        }
        if (this.maintenanceCharge == null) {
            this.maintenanceCharge = 0.0;
        }
        if (this.breakageCharge == null) {
            this.breakageCharge = 0.0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
