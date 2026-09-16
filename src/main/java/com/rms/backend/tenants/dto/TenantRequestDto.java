package com.rms.backend.tenants.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TenantRequestDto {

    private String uid;

    @NotBlank(message = "Name is required")
    private String name;

    private String aadhaarNo;

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @NotBlank(message = "Tenant type is required (Working / Student)")
    private String tenantType;

    @NotBlank(message = "Organization name is required (Company / School)")
    private String organizationName;

    private String parentContact; // Nullable

    @NotBlank(message = "Room number is required")
    private String roomNo;

    @NotNull(message = "Advance paid is required")
    @Min(value = 0, message = "Advance paid cannot be negative")
    private Integer advancePaid;

    @NotNull(message = "Standard rent is required")
    @Min(value = 0, message = "Standard rent cannot be negative")
    private Integer standardRent;

    private Long propertyId;
    private String status;

    // Getters and Setters

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAadhaarNo() {
        return aadhaarNo;
    }

    public void setAadhaarNo(String aadhaarNo) {
        this.aadhaarNo = aadhaarNo;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getTenantType() {
        return tenantType;
    }

    public void setTenantType(String tenantType) {
        this.tenantType = tenantType;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getParentContact() {
        return parentContact;
    }

    public void setParentContact(String parentContact) {
        this.parentContact = parentContact;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public Integer getAdvancePaid() {
        return advancePaid;
    }

    public void setAdvancePaid(Integer advancePaid) {
        this.advancePaid = advancePaid;
    }

    public Integer getStandardRent() {
        return standardRent;
    }

    public void setStandardRent(Integer standardRent) {
        this.standardRent = standardRent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
