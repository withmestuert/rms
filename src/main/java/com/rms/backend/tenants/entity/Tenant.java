package com.rms.backend.tenants.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @Column(name = "uid", nullable = false)
    private String uid; // Primary Key

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "aadhaar_no", unique = true, nullable = false)
    private String aadhaarNo; // Unique (Candidate Key)

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "tenant_type", nullable = false)
    private String tenantType; // Working / Student

    @Column(name = "organization_name", nullable = false)
    private String organizationName; // Company / School

    @Column(name = "parent_contact")
    private String parentContact; // Nullable

    @Column(name = "room_no", nullable = false)
    private String roomNo; // References Rooms

    @Column(name = "advance_paid", nullable = false)
    private Integer advancePaid;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.ColumnDefault("'PENDING'")
    @Column(name = "advance_paid_status", nullable = false)
    private AdvancePaidStatus advancePaidStatus;

    @Column(name = "standard_rent", nullable = false)
    private Integer standardRent;

    @Column(name = "property_id")
    private Long propertyId;

    public Tenant(String uid, String name, String aadhaarNo, String mobileNumber,
                  String tenantType, String organizationName, String parentContact,
                  String roomNo, Integer advancePaid, Integer standardRent) {
        this(uid, name, aadhaarNo, mobileNumber, tenantType, organizationName, parentContact, roomNo, advancePaid, AdvancePaidStatus.PENDING, standardRent, null);
    }

    public Tenant(String uid, String name, String aadhaarNo, String mobileNumber,
                  String tenantType, String organizationName, String parentContact,
                  String roomNo, Integer advancePaid, AdvancePaidStatus advancePaidStatus,
                  Integer standardRent) {
        this(uid, name, aadhaarNo, mobileNumber, tenantType, organizationName, parentContact, roomNo, advancePaid, advancePaidStatus, standardRent, null);
    }

    @PrePersist
    protected void onCreate() {
        if (this.advancePaidStatus == null) {
            this.advancePaidStatus = AdvancePaidStatus.PENDING;
        }
    }
}
